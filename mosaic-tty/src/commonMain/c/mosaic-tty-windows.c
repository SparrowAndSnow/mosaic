#if defined(_WIN32)

#include "mosaic-tty-windows.h"
#include "mosaic-utils-windows.h"

#include <assert.h>
#include <stdatomic.h>
#include <windows.h>

// How often an otherwise blocking read wakes up to repair a console mode reset from outside.
enum { modeRepairIntervalMillis = 1000 };

MosaicTtyInitResult mosaic_tty_init_with_handles(
	HANDLE conin,
	HANDLE conoutForSize,
	HANDLE conoutForWrite,
	bool conoutForWriteFake
) {
	MosaicTtyInitResult result = {};

	MosaicTtyImpl *tty = calloc(1, sizeof(MosaicTtyImpl));
	if (unlikely(tty == NULL)) {
		// result.tty is set to 0 which will trigger OOM.
		goto ret;
	}

	HANDLE coninInterruptEvent = CreateEvent(NULL, FALSE, FALSE, NULL);
	if (unlikely(coninInterruptEvent == NULL)) {
		result.error = GetLastError();
		goto err_free;
	}

	tty->conin = conin;
	tty->conin_interrupt_event = coninInterruptEvent;
	tty->conout_for_write = conoutForWrite;
	tty->conout_for_write_fake = conoutForWriteFake;
	tty->conout_for_size = conoutForSize;

	result.tty = tty;

	ret:
	return result;

	err_free:
	free(tty);
	goto ret;
}

static _Atomic(MosaicTty *) globalTty;

MOSAIC_EXPORT MosaicTtyInitResult mosaic_tty_init(void) {
	MosaicTtyInitResult result = {};

	HANDLE conin = CreateFile(TEXT("CONIN$"), GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE, 0, OPEN_EXISTING, 0, 0);
	if (unlikely(conin == INVALID_HANDLE_VALUE)) {
		goto err;
	}
	HANDLE conout = CreateFile(TEXT("CONOUT$"), GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE, 0, OPEN_EXISTING, 0, 0);
	if (unlikely(conout == INVALID_HANDLE_VALUE)) {
		goto err;
	}

	HANDLE stdin = GetStdHandle(STD_INPUT_HANDLE);
	if (unlikely(stdin == INVALID_HANDLE_VALUE)) {
		goto err;
	}
	HANDLE stdout = GetStdHandle(STD_OUTPUT_HANDLE);
	if (unlikely(stdout == INVALID_HANDLE_VALUE)) {
		goto err;
	}
	HANDLE stderr = GetStdHandle(STD_ERROR_HANDLE);
	if (unlikely(stderr == INVALID_HANDLE_VALUE)) {
		goto err;
	}

	result = mosaic_tty_init_with_handles(conin, conout, conout, false);

	MosaicTty *tty = result.tty;
	MosaicTty *expected = NULL;
	if (likely(tty) && !atomic_compare_exchange_strong(&globalTty, &expected, tty)) {
		// We initialized an instance but there already was a global instance.
		result.tty = NULL;
		result.error = mosaic_tty_free(tty);
		result.already_bound = true;
	}

	ret:
	return result;

	err:
	result.error = GetLastError();
	goto ret;
}

MOSAIC_EXPORT void mosaic_tty_set_callback(MosaicTty *tty, MosaicTtyCallback *callback) {
	tty->callback = callback;
}

MOSAIC_EXPORT MosaicIoResult mosaic_tty_read(
	MosaicTty *tty,
	uint8_t *buffer,
	int count
) {
	return mosaic_tty_read_with_timeout(tty, buffer, count, INFINITE);
}

// Deliver a resize callback when the console's window size differs from the last reported one.
// Called after every read drains records, because ReadFile consumes every pending record (including
// resize events, which it cannot dispatch) and conhost reports a resize as a burst of records.
static void mosaic_tty_dispatch_resize(MosaicTty *tty) {
	MosaicTtyCallback *callback = tty->callback;
	if (callback == NULL) {
		return;
	}
	CONSOLE_SCREEN_BUFFER_INFO info;
	if (unlikely(!GetConsoleScreenBufferInfo(tty->conout_for_size, &info))) {
		return;
	}
	int columns = info.srWindow.Right - info.srWindow.Left + 1;
	int rows = info.srWindow.Bottom - info.srWindow.Top + 1;
	if (columns == tty->last_columns && rows == tty->last_rows) {
		return;
	}
	tty->last_columns = columns;
	tty->last_rows = rows;
	callback->onResize(callback->opaque, columns, rows, 0, 0);
}

// Repair the console modes if something outside the TTY reset them while it is open. An audio
// engine (or a driver it loads) entering the process can reset the console, which leaves the
// console line-buffered and echoed: every read then blocks until the user presses Enter, and the
// terminal echoes what they type. Re-applying the modes the console accepted at raw-mode time
// restores VT input without needing the application to notice anything.
static void mosaic_tty_reassert_modes(MosaicTty *tty) {
	if (unlikely(tty->saved_input_mode == 0)) {
		return; // Raw mode is not enabled.
	}
	DWORD mode;
	if (likely(GetConsoleMode(tty->conin, &mode))) {
		if (unlikely(mode != tty->raw_input_mode)) {
			SetConsoleMode(tty->conin, tty->raw_input_mode);
		}
	}
	if (!tty->conout_for_write_fake) {
		if (likely(GetConsoleMode(tty->conout_for_write, &mode))) {
			if (unlikely(mode != tty->raw_output_mode)) {
				SetConsoleMode(tty->conout_for_write, tty->raw_output_mode);
			}
		}
		if (unlikely(GetConsoleOutputCP() != tty->raw_output_code_page)) {
			SetConsoleOutputCP(tty->raw_output_code_page);
		}
	}
}

MOSAIC_EXPORT MosaicIoResult mosaic_tty_read_with_timeout(
	MosaicTty *tty,
	uint8_t *buffer,
	int count,
	int timeoutMillis
) {
	MosaicIoResult result = {};

	DWORD waitResult;
	HANDLE waitHandles[2] = { tty->conin, tty->conin_interrupt_event };

	loop:
	// An interrupt must win over pending console records: WaitForMultipleObjects reports only the
	// lowest-indexed signaled handle, so a signaled console would otherwise hide the interrupt
	// until those records are consumed.
	if (unlikely(WaitForSingleObject(tty->conin_interrupt_event, 0) == WAIT_OBJECT_0)) {
		goto ret;
	}

	mosaic_tty_reassert_modes(tty);

	// A blocking read wakes periodically so a console reset is repaired even while the terminal is
	// idle. A timeout from this internal interval must not be reported as an interrupt.
	waitResult = WaitForMultipleObjects(2, waitHandles, FALSE,
		(DWORD) timeoutMillis == INFINITE ? (DWORD) modeRepairIntervalMillis : (DWORD) timeoutMillis);
	if (likely(waitResult == WAIT_OBJECT_0)) {
		// Peek at pending events without consuming them, so that ReadFile
		// can later consume keyboard/mouse INPUT_RECORDs as VT sequences.
		// FOCUS_EVENT and WINDOW_BUFFER_SIZE_EVENT only exist as INPUT_RECORDs
		// and are handled via callbacks.
		INPUT_RECORD *records = tty->records;
		DWORD recordsRead = 0;
		if (unlikely(!PeekConsoleInputW(tty->conin, records, recordsCount, &recordsRead))) {
			goto err;
		}

		MosaicTtyCallback *callback = tty->callback;
		bool hadKeyboardEvents = false;

		for (DWORD i = 0; i < recordsRead; i++) {
			INPUT_RECORD *rec = &records[i];

			switch (rec->EventType) {
			case KEY_EVENT:
				// Only a key press makes ReadFile produce bytes. A key release, a mouse record, or
				// a focus/resize record can leave ReadFile waiting for a later key press instead of
				// returning, which freezes every other input path (including a timed read) until
				// that happens. Such records are dispatched or discarded below instead.
				if (rec->Event.KeyEvent.bKeyDown) {
					hadKeyboardEvents = true;
				}
				break;

			case FOCUS_EVENT:
				if (callback) {
					callback->onFocus(callback->opaque, rec->Event.FocusEvent.bSetFocus);
				}
				break;

			case WINDOW_BUFFER_SIZE_EVENT:
				// Dispatched after the records are consumed, by comparing against the last reported
				// size. A resize which only exists as a record must never leave the record behind.
				break;
			}
		}

		if (hadKeyboardEvents) {
			// Read VT sequences for keyboard input. With ENABLE_VIRTUAL_TERMINAL_INPUT, the console
			// encodes key presses as VT escape sequences in UTF-8, including full Unicode support
			// for IME-composed characters. A key press is pending, so this cannot block. The call
			// consumes every pending record, including the ones dispatched above.
			DWORD bytesRead = 0;
			BOOL ok = ReadFile(tty->conin, buffer, count, &bytesRead, NULL);
			if (ok && bytesRead > 0) {
				result.count = bytesRead;
			} else if (!ok) {
				result.error = GetLastError();
			}
		} else if (recordsRead > 0) {
			// No key press is pending, so the records are focus/resize events (dispatched above),
			// key releases, or mouse records. None of them can be left in the buffer: the console
			// would stay signaled forever and this loop would spin on the very same records -
			// burning a core, re-dispatching the same resize thousands of times per second
			// (flooding the event channel so real input gets dropped), and never observing an
			// interrupt. Consume them so the blocking wait below blocks for real.
			DWORD recordsDiscarded = 0;
			if (unlikely(!ReadConsoleInputW(tty->conin, records, recordsRead, &recordsDiscarded))) {
				goto err;
			}
		}

		if (tty->window_resize_events) {
			// ReadFile consumes every pending record, not just the keyboard/mouse ones it
			// translates, so a resize can otherwise be swallowed between the peek and the read.
			mosaic_tty_dispatch_resize(tty);
		}

		if (result.count == 0 && result.error == 0) {
			if (unlikely(recordsRead == 0)) {
				// Signaled without any records to consume. Never spin on that.
				Sleep(1);
			}
			goto loop;
		}
	} else if (unlikely(waitResult == WAIT_FAILED)) {
		goto err;
	} else if (waitResult == WAIT_TIMEOUT && (DWORD) timeoutMillis == INFINITE) {
		goto loop;
	}

	ret:
	return result;

	err:
	result.error = GetLastError();
	goto ret;
}

MOSAIC_EXPORT uint32_t mosaic_tty_interrupt_read(MosaicTty *tty) {
	return likely(SetEvent(tty->conin_interrupt_event) != 0)
		? 0
		: GetLastError();
}

MOSAIC_EXPORT MosaicIoResult mosaic_tty_write(MosaicTty *tty, uint8_t *buffer, int count) {
	return mosaic_utils_write(tty->conout_for_write, buffer, count);
}

MOSAIC_EXPORT uint32_t mosaic_tty_enable_raw_mode(MosaicTty *tty) {
	uint32_t result = 0;

	if (tty->saved_input_mode) {
		// Already enabled. Re-assert so the caller can repair a console reset performed by something
		// else in the process.
		mosaic_tty_reassert_modes(tty);
		goto ret;
	}

	DWORD input_mode;
	DWORD output_mode;
	UINT output_code_page;
	if (unlikely(GetConsoleMode(tty->conin, &input_mode) == 0)) {
		result = GetLastError();
		goto ret;
	}
	if (!tty->conout_for_write_fake) {
		if (unlikely(GetConsoleMode(tty->conout_for_write, &output_mode) == 0)) {
			result = GetLastError();
			goto ret;
		}
		if (unlikely((output_code_page = GetConsoleOutputCP()) == 0)) {
			result = GetLastError();
			goto ret;
		}
	}

	// https://learn.microsoft.com/en-us/windows/console/setconsolemode
	const int coninMode = 0
		// Disable quick edit mode.
		| ENABLE_EXTENDED_FLAGS
		// Report changes to the mouse position.
		| ENABLE_MOUSE_INPUT
		// Encode key and mouse events as VT sequences rather than input records.
		| ENABLE_VIRTUAL_TERMINAL_INPUT
		// Report changes to the buffer size.
		| ENABLE_WINDOW_INPUT
		;
	const int conoutMode = 0
		// Do not wrap cursor to next line automatically when writing final column.
		| DISABLE_NEWLINE_AUTO_RETURN
		// Allow color sequences to affect characters in all locales.
		| ENABLE_LVB_GRID_WORLDWIDE
		// Process outgoing VT sequences for colors, etc.
		| ENABLE_PROCESSED_OUTPUT
		// Process outgoing VT sequences for cursor movement, etc.
		| ENABLE_VIRTUAL_TERMINAL_PROCESSING
		;
	// UTF-8 per https://learn.microsoft.com/en-us/windows/win32/intl/code-page-identifiers.
	const int conoutCp = 65001;

	if (unlikely(SetConsoleMode(tty->conin, coninMode) == 0)) {
		result = GetLastError();
		goto ret;
	}
	if (!tty->conout_for_write_fake) {
		if (unlikely(SetConsoleMode(tty->conout_for_write, conoutMode) == 0)) {
			result = GetLastError();
			SetConsoleMode(tty->conin, input_mode);
			goto ret;
		}
		if (unlikely(SetConsoleOutputCP(conoutCp) == 0)) {
			result = GetLastError();
			SetConsoleMode(tty->conin, input_mode);
			SetConsoleMode(tty->conout_for_write, output_mode);
			goto ret;
		}
	}

	tty->saved_input_mode = input_mode;
	tty->saved_output_mode = output_mode;
	tty->saved_output_code_page = output_code_page;

	// Record what the console actually accepted rather than what was requested: the host may clear
	// bits (such as ENABLE_MOUSE_INPUT next to ENABLE_VIRTUAL_TERMINAL_INPUT), and comparing against
	// the requested value would then rewrite the mode on every check.
	DWORD applied;
	if (likely(GetConsoleMode(tty->conin, &applied))) {
		tty->raw_input_mode = applied;
	} else {
		tty->raw_input_mode = coninMode;
	}
	if (!tty->conout_for_write_fake) {
		if (likely(GetConsoleMode(tty->conout_for_write, &applied))) {
			tty->raw_output_mode = applied;
		} else {
			tty->raw_output_mode = conoutMode;
		}
		tty->raw_output_code_page = conoutCp;
	}

	ret:
	return result;
}

MOSAIC_EXPORT uint32_t mosaic_tty_enable_window_resize_events(MosaicTty *tty) {
	// Seed the tracked size so enabling resize events never reports a resize that did not happen.
	CONSOLE_SCREEN_BUFFER_INFO info;
	if (likely(GetConsoleScreenBufferInfo(tty->conout_for_size, &info))) {
		tty->last_columns = info.srWindow.Right - info.srWindow.Left + 1;
		tty->last_rows = info.srWindow.Bottom - info.srWindow.Top + 1;
	}
	tty->window_resize_events = true;
	return 0;
}

MOSAIC_EXPORT MosaicTtyTerminalSizeResult mosaic_tty_current_terminal_size(MosaicTty *tty) {
	MosaicTtyTerminalSizeResult result = {};

	CONSOLE_SCREEN_BUFFER_INFO info;
	if (likely(GetConsoleScreenBufferInfo(tty->conout_for_size, &info))) {
		result.columns = info.srWindow.Right - info.srWindow.Left + 1;
		result.rows = info.srWindow.Bottom - info.srWindow.Top + 1;
	} else {
		result.error = GetLastError();
	}

	return result;
}

MOSAIC_EXPORT uint32_t mosaic_tty_enable_signal_guard(MosaicTty *tty) {
	// No POSIX signals on Windows; the console is restored through the JVM shutdown hooks.
	(void)tty;
	return 0;
}

MOSAIC_EXPORT uint32_t mosaic_tty_disable_signal_guard(MosaicTty *tty) {
	(void)tty;
	return 0;
}

MOSAIC_EXPORT uint32_t mosaic_tty_reset(MosaicTty *tty) {
	uint32_t result = 0;

	if (tty->saved_input_mode) {
		if (unlikely(!SetConsoleMode(tty->conin, tty->saved_input_mode))) {
			result = GetLastError();
		}
		if (!tty->conout_for_write_fake) {
			if (unlikely(!SetConsoleMode(tty->conout_for_write, tty->saved_output_mode) && result == 0)) {
				result = GetLastError();
			}
			if (unlikely(!SetConsoleOutputCP(tty->saved_output_code_page) && result == 0)) {
				result = GetLastError();
			}
		}
		tty->saved_input_mode = 0;
		tty->saved_output_mode = 0;
		tty->saved_output_code_page = 0;
		tty->raw_input_mode = 0;
		tty->raw_output_mode = 0;
		tty->raw_output_code_page = 0;
	}

	return result;
}

MOSAIC_EXPORT uint32_t mosaic_tty_free(MosaicTty *tty) {
	uint32_t result = 0;

	if (unlikely(CloseHandle(tty->conin_interrupt_event) == 0)) {
		result = GetLastError();
	}

	uint32_t resetResult = mosaic_tty_reset(tty);
	if (resetResult != 0 && result == 0) {
		result = resetResult;
	}

	atomic_store(&globalTty, NULL);
	free(tty);
	return result;
}

#endif
