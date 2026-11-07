#include <jni.h>
#include <unistd.h>
#include <sys/prctl.h>
#include <sys/wait.h>
#include <linux/prctl.h>

static void raise_all_ambient(void) {
    prctl(PR_CAP_AMBIENT, PR_CAP_AMBIENT_CLEAR_ALL, 0, 0, 0);
    for (int cap = 0; cap <= 40; cap++) {
        prctl(PR_CAP_AMBIENT, PR_CAP_AMBIENT_RAISE, cap, 0, 0);
    }
}

__attribute__((visibility("default")))
jint Agent_OnAttach(JavaVM *vm, char *args, void *reserved) {
    (void)vm;
    (void)reserved;
    if (!args || !*args) {
        return JNI_ERR;
    }
    pid_t pid = fork();
    if (pid == 0) {
        raise_all_ambient();
        execl("/system/bin/sh", "sh", args, (char *)0);
        _exit(127);
    }
    if (pid > 0) {
        int status = 0;
        waitpid(pid, &status, 0);
    }
    return JNI_OK;
}
