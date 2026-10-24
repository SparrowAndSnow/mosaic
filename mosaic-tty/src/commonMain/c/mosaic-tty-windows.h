#ifndef MOSAIC_TTY_WINDOWS_H
#define MOSAIC_TTY_WINDOWS_H

#include "mosaic-tty.h"

#include <windows.h>

enum { recordsCount = 64 };

typedef struct MosaicTtyImpl {
	HANDLE conin;
	HANDLE conin_interrupt_event;
	HANDLE conout_for_write;
	bool conout_for_write_fake;
	HANDLE conout_for_size;
	INPUT_RECORD records[recordsCount];
	MosaicTtyCallback *callback;
	bool window_resize_events;
	// Last size reported to the callback. Used to deliver exactly one resize per actual change, and
	// to notice a resize whose record was consumed by ReadFile before it could be dispatched.
	int last_columns;
	int last_rows;
	DWORD saved_input_mode;
	DWORD saved_output_mode;
	UINT saved_output_code_page;
	// Modes the console actually accepted when raw mode was enabled, used to detect (and repair) a
	// console reset performed by something outside the TTY, such as an audio engine.
	DWORD raw_input_mode;
	DWORD raw_output_mode;
	UINT raw_output_code_page;
} MosaicTtyImpl;

MosaicTtyInitResult mosaic_tty_init_with_handles(
	HANDLE conin,
	HANDLE conoutForSize,
	HANDLE conoutForWrite,
	bool conoutForWriteFake
);

#endif // MOSAIC_TTY_WINDOWS_H
