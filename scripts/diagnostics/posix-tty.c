#define _XOPEN_SOURCE 600
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <termios.h>
#include <unistd.h>

int main(int argc, char **argv) {
	if (argc != 2) return 2;
	int parent = posix_openpt(O_RDWR | O_NOCTTY);
	if (parent < 0 || grantpt(parent) || unlockpt(parent)) { perror("pty"); return 1; }
	int child = open(ptsname(parent), O_RDWR | O_NOCTTY);
	struct termios saved;
	if (child < 0 || tcgetattr(child, &saved)) { perror("child"); return 1; }
	struct termios raw = saved;
	raw.c_iflag &= ~(BRKINT | ICRNL | IGNBRK | IGNCR | INLCR | ISTRIP | IXON | PARMRK);
	raw.c_oflag &= ~OPOST;
	raw.c_lflag &= ~(ECHO | ECHONL | ICANON | IEXTEN | ISIG);
	raw.c_cflag &= ~(CSIZE | PARENB);
	raw.c_cflag |= CS8;
	raw.c_cc[VMIN] = 1;
	raw.c_cc[VTIME] = 0;
	if (tcsetattr(child, TCSAFLUSH, &raw)) { perror("raw"); return 1; }
	const char query[] = "\033[0c";
	if (write(child, query, sizeof(query) - 1) != sizeof(query) - 1) return 1;
	if (strcmp(argv[1], "flush-drained") == 0) {
		char received[sizeof(query)];
		if (read(parent, received, sizeof(query) - 1) != sizeof(query) - 1) return 1;
	}
	printf("before restore: %s\n", argv[1]);
	fflush(stdout);
	int action = strcmp(argv[1], "now") == 0 ? TCSANOW : TCSAFLUSH;
	int result = tcsetattr(child, action, &saved);
	printf("after restore: result=%d errno=%d\n", result, result ? errno : 0);
	fflush(stdout);
	close(child);
	close(parent);
	return result != 0;
}
