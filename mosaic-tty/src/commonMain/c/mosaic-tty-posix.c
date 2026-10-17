#if defined(__APPLE__) || defined(__linux__)

#include "mosaic-tty-posix.h"
#include "mosaic-utils-posix.h"

#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <termios.h>
#include <time.h>
#include <unistd.h>

static _Atomic(MosaicTty *) globalTty;

MosaicTtyInitResult mosaic_tty_init_with_fd(int fd) {
	MosaicTtyInitResult result = {};

	MosaicTtyImpl *tty = calloc(1, sizeof(MosaicTtyImpl));
	if (unlikely(tty == NULL)) {
		// result.tty is set to 0 which will trigger OOM.
		goto ret;
	}

	int interruptPipe[2];
	if (unlikely(pipe(interruptPipe)) != 0) {
		result.error = errno;
		goto err;
	}

	tty->fd = fd;
	tty->interrupt_fd_reader = interruptPipe[0];
	tty->interrupt_fd_writer = interruptPipe[1];

	result.tty = tty;

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
	free(tty);
	goto ret;
}

MosaicTtyInitResult mosaic_tty_init(void) {
	int fd = open("/dev/tty", O_RDWR);
	if (likely(fd != -1)) {
		return mosaic_tty_init_with_fd(fd);
	}

	MosaicTtyInitResult result = {};
	result.error = errno;
	result.no_tty = true;
	return result;
}

void mosaic_tty_set_callback(MosaicTty *tty, MosaicTtyCallback *callback) {
	tty->callback = callback;
}

MosaicIoResult mosaic_tty_read(MosaicTty *tty, uint8_t *buffer, int count) {
	return mosaic_utils_read(tty->fd, tty->interrupt_fd_reader, buffer, count, NULL);
}

MosaicIoResult mosaic_tty_read_with_timeout(
	MosaicTty *tty,
	uint8_t *buffer,
	int count,
	int timeoutMillis
) {
	struct timeval timeout;
	timeout.tv_sec = 0;
	timeout.tv_usec = timeoutMillis * 1000;

	return mosaic_utils_read(tty->fd, tty->interrupt_fd_reader, buffer, count, &timeout);
}

uint32_t mosaic_tty_interrupt_read(MosaicTty *tty) {
	uint8_t space = ' ';
	MosaicIoResult result = mosaic_utils_write(tty->interrupt_fd_writer, &space, 1);
	return result.error;
}

MosaicIoResult mosaic_tty_write(MosaicTty *tty, uint8_t *buffer, int count) {
	return mosaic_utils_write(tty->fd, buffer, count);
}

static void mosaic_tty_sigwinch_handler(int value UNUSED) {
	MosaicTty *tty = atomic_load(&globalTty);
	if (likely(tty)) {
		struct winsize size;
		if (ioctl(tty->fd, TIOCGWINSZ, &size) != -1) {
			MosaicTtyCallback *callback = tty->callback;
			if (likely(callback)) {
				callback->onResize(callback->opaque, size.ws_col, size.ws_row, size.ws_xpixel, size.ws_ypixel);
			} else {
				// TODO Send warning somewhere? Maybe once we get debug logs working.
			}
		} else {
			// TODO Send errno somewhere? Maybe once we get debug logs working.
		}
	} else {
		// TODO Send warning somewhere? Maybe once we get debug logs working.
	}
}

uint32_t mosaic_tty_enable_raw_mode(MosaicTty *tty) {
	uint32_t result = 0;

	if (unlikely(tty->saved)) {
		goto ret; // Already enabled!
	}

	struct termios *saved = calloc(1, sizeof(struct termios));
	if (unlikely(saved == NULL)) {
		result = ENOMEM;
		goto ret;
	}

	if (unlikely(tcgetattr(tty->fd, saved) != 0)) {
		result = errno;
		goto err;
	}

	struct termios current = (*saved);

	// Flags as defined by "Raw mode" section of https://linux.die.net/man/3/termios
	current.c_iflag &= ~(BRKINT | ICRNL | IGNBRK | IGNCR | INLCR | ISTRIP | IXON | PARMRK);
	current.c_oflag &= ~(OPOST);
	// Setting ECHONL should be useless here, but it is what is documented for cfmakeraw.
	current.c_lflag &= ~(ECHO | ECHONL | ICANON | IEXTEN | ISIG);
	current.c_cflag &= ~(CSIZE | PARENB);
	current.c_cflag |= (CS8);

	current.c_cc[VMIN] = 1;
	current.c_cc[VTIME] = 0;

	if (unlikely(tcsetattr(tty->fd, TCSAFLUSH, &current) != 0)) {
		result = errno;
		// Try to restore the saved config.
		tcsetattr(tty->fd, TCSAFLUSH, saved);
		goto err;
	}

	tty->saved = saved;

	ret:
	return result;

	err:
	free(saved);
	goto ret;
}

uint32_t mosaic_tty_enable_window_resize_events(MosaicTty *tty) {
	if (tty->sigwinch) {
		return 0; // Already installed.
	}

	struct sigaction action;
	action.sa_handler = mosaic_tty_sigwinch_handler;
	sigemptyset(&action.sa_mask);
	action.sa_flags = 0;

	if (likely(sigaction(SIGWINCH, &action, NULL) == 0)) {
		tty->sigwinch = true;
		return 0;
	}
	return errno;
}

MosaicTtyTerminalSizeResult mosaic_tty_current_terminal_size(MosaicTty *tty) {
	MosaicTtyTerminalSizeResult result = {};

	struct winsize size;
	if (ioctl(tty->fd, TIOCGWINSZ, &size) != -1) {
		result.columns = size.ws_col;
		result.rows = size.ws_row;
		result.width = size.ws_xpixel;
		result.height = size.ws_ypixel;
	} else {
		result.error = errno;
	}

	return result;
}

static const int mosaic_tty_signal_guard_signals[] = { SIGSEGV, SIGILL, SIGPIPE };
#define MOSAIC_TTY_SIGNAL_GUARD_COUNT 3

static void mosaic_tty_signal_guard_handler(int value) {
	MosaicTty *tty = atomic_load(&globalTty);
	if (likely(tty)) {
		if (tty->saved) {
			// Best-effort restore of the original terminal settings. tcsetattr is not in the
			// async-signal-safe set, but on Linux/BSD it is a direct ioctl with no user-space
			// locking, which is the standard approach interactive terminal programs use so that
			// a crash never leaves the terminal in raw mode.
			tcsetattr(tty->fd, TCSAFLUSH, tty->saved);
		}
		// Wake any reader blocked in mosaic_tty_read so the application observes the interruption.
		uint8_t space = ' ';
		ssize_t ignored = write(tty->interrupt_fd_writer, &space, 1);
		(void)ignored;
	}

	// Preserve the process-terminating semantics of the signal: restore the default disposition
	// and re-raise so the process dies exactly as it would have without the guard (core dumps,
	// waitpid status, and exit codes are unchanged). Unblock first so termination is not deferred
	// if this thread happened to have the signal blocked.
	sigset_t set;
	sigemptyset(&set);
	sigaddset(&set, value);
	sigprocmask(SIG_UNBLOCK, &set, NULL);

	struct sigaction action;
	action.sa_handler = SIG_DFL;
	sigemptyset(&action.sa_mask);
	action.sa_flags = 0;
	if (sigaction(value, &action, NULL) == 0) {
		raise(value);
	}
	// Only reached if the re-raise unexpectedly failed to terminate the process.
	_exit(128 + value);
}

uint32_t mosaic_tty_enable_signal_guard(MosaicTty *tty) {
	if (tty->signal_guard_active) {
		return 0; // Already installed.
	}

	uint32_t result = 0;

	struct sigaction action;
	action.sa_handler = mosaic_tty_signal_guard_handler;
	sigemptyset(&action.sa_mask);
	action.sa_flags = 0;

	for (int i = 0; i < MOSAIC_TTY_SIGNAL_GUARD_COUNT; i++) {
		int signal_i = mosaic_tty_signal_guard_signals[i];
		struct sigaction previous;
		if (unlikely(sigaction(signal_i, NULL, &previous) != 0)) {
			result = errno;
			continue;
		}
		if (previous.sa_handler != SIG_DFL) {
			// Respect an existing custom or ignored disposition rather than hijacking deliberate
			// handling (for example a runtime which ignores SIGPIPE, or its own crash reporting).
			continue;
		}
		if (unlikely(sigaction(signal_i, &action, &tty->signal_guard_previous[i]) != 0)) {
			result = errno;
			continue;
		}
		tty->signal_guard_installed[i] = true;
	}

	tty->signal_guard_active = true;
	return result;
}

uint32_t mosaic_tty_disable_signal_guard(MosaicTty *tty) {
	uint32_t result = 0;

	for (int i = 0; i < MOSAIC_TTY_SIGNAL_GUARD_COUNT; i++) {
		if (!tty->signal_guard_installed[i]) {
			continue;
		}
		// Only restore the previous disposition if it is still our handler; the application may
		// have changed the disposition after we installed ours, and we must not clobber that.
		struct sigaction current;
		if (likely(sigaction(mosaic_tty_signal_guard_signals[i], NULL, &current) == 0) &&
				current.sa_handler == mosaic_tty_signal_guard_handler) {
			if (unlikely(sigaction(mosaic_tty_signal_guard_signals[i], &tty->signal_guard_previous[i], NULL) != 0)) {
				result = errno;
			}
		}
		tty->signal_guard_installed[i] = false;
	}

	tty->signal_guard_active = false;
	return result;
}

uint32_t mosaic_tty_reset(MosaicTty *tty) {
	uint32_t result = 0;

	if (tty->sigwinch) {
		if (unlikely(signal(SIGWINCH, SIG_DFL) == SIG_ERR)) {
			result = errno;
		}
		tty->sigwinch = false;
	}

	if (tty->saved) {
		if (unlikely(tcsetattr(tty->fd, TCSAFLUSH, tty->saved) && result == 0)) {
			result = errno;
		}
		free(tty->saved);
		tty->saved = NULL;
	}

	uint32_t guardResult = mosaic_tty_disable_signal_guard(tty);
	if (guardResult != 0 && result == 0) {
		result = guardResult;
	}

	return result;
}

uint32_t mosaic_tty_free(MosaicTty *tty) {
	uint32_t result = 0;

	uint32_t resetResult = mosaic_tty_reset(tty);
	if (resetResult != 0) {
		result = resetResult;
	}

	if (unlikely(close(tty->fd) != 0 && result == 0)) {
		result = errno;
	}

	if (unlikely(close(tty->interrupt_fd_reader) != 0 && result == 0)) {
		result = errno;
	}
	if (unlikely(close(tty->interrupt_fd_writer) != 0 && result == 0)) {
		result = errno;
	}

	atomic_store(&globalTty, NULL);
	free(tty);
	return result;
}

#endif
