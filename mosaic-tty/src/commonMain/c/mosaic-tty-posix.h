#ifndef MOSAIC_TTY_POSIX_H
#define MOSAIC_TTY_POSIX_H

#include "mosaic-tty.h"

#include <signal.h>

typedef struct MosaicTtyImpl {
	int fd;
	int interrupt_fd_reader;
	int interrupt_fd_writer;
	MosaicTtyCallback *callback;
	bool sigwinch;
	struct termios *saved;
	bool signal_guard_active;
	bool signal_guard_installed[3];
	struct sigaction signal_guard_previous[3];
} MosaicTtyImpl;

MosaicTtyInitResult mosaic_tty_init_with_fd(int ttyFd);

#endif // MOSAIC_TTY_POSIX_H
