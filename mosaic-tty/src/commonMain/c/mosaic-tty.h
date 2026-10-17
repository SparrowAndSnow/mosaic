#ifndef MOSAIC_TTY_H
#define MOSAIC_TTY_H

#include "mosaic-utils.h"

#include <stdbool.h>
#include <stdint.h>

typedef struct MosaicTtyImpl MosaicTty;

typedef void MosaicTtyCallbackOnFocus(void *opaque, bool focused);
typedef void MosaicTtyCallbackOnResize(void *opaque, int columns, int rows, int width, int height);

typedef struct MosaicTtyCallback {
	void *opaque;
	MosaicTtyCallbackOnFocus *onFocus;
	MosaicTtyCallbackOnResize *onResize;
} MosaicTtyCallback;

typedef struct MosaicTtyInitResult {
	MosaicTty *tty;
	uint32_t error;
	bool no_tty;
	bool already_bound;
} MosaicTtyInitResult;

typedef struct MosaicTtyTerminalSizeResult {
	int columns;
	int rows;
	int width;
	int height;
	uint32_t error;
} MosaicTtyTerminalSizeResult;

MOSAIC_EXPORT MosaicTtyInitResult mosaic_tty_init(void);

MOSAIC_EXPORT void mosaic_tty_set_callback(MosaicTty *tty, MosaicTtyCallback *callback);

MOSAIC_EXPORT MosaicIoResult mosaic_tty_read(MosaicTty *tty, uint8_t *buffer, int count);
MOSAIC_EXPORT MosaicIoResult mosaic_tty_read_with_timeout(MosaicTty *tty, uint8_t *buffer, int count, int timeoutMillis);
MOSAIC_EXPORT uint32_t mosaic_tty_interrupt_read(MosaicTty *tty);

MOSAIC_EXPORT MosaicIoResult mosaic_tty_write(MosaicTty *tty, uint8_t *buffer, int count);

MOSAIC_EXPORT uint32_t mosaic_tty_enable_raw_mode(MosaicTty *tty);
MOSAIC_EXPORT uint32_t mosaic_tty_enable_window_resize_events(MosaicTty *tty);
MOSAIC_EXPORT MosaicTtyTerminalSizeResult mosaic_tty_current_terminal_size(MosaicTty *tty);

/**
 * Install a best-effort signal safety net for the process-terminating signals (SIGSEGV, SIGILL,
 * SIGPIPE) which are otherwise unhandled and would leave the terminal in a raw state with no
 * cleanup. Only signals whose current disposition is the default are guarded; a custom or ignored
 * disposition is always left untouched. When a guarded signal is delivered the original terminal
 * settings are restored (best-effort) and a blocked reader is woken before the signal is re-raised
 * with its default disposition so the process terminates unchanged.
 *
 * The guard is process-global in effect but is scoped to the supplied [tty]. It must be paired
 * with a call to [mosaic_tty_disable_signal_guard] (which [mosaic_tty_reset] performs).
 */
MOSAIC_EXPORT uint32_t mosaic_tty_enable_signal_guard(MosaicTty *tty);
MOSAIC_EXPORT uint32_t mosaic_tty_disable_signal_guard(MosaicTty *tty);

MOSAIC_EXPORT uint32_t mosaic_tty_reset(MosaicTty *tty);
MOSAIC_EXPORT uint32_t mosaic_tty_free(MosaicTty *tty);

#endif // MOSAIC_TTY_H
