/*
 * QuestCraft: `java` for programs on Android, where an app may only run programs from its native
 * library folder (an APK's lib/<abi>, extracted on install), never from its own files. Java
 * runtimes it downloads (for Horizon's preparer: Java 25) sit in its files, so their bin/java can't
 * be started; this stands in for it. Shipped in the APK as libhqjava.so, it is started as `java`
 * would be, with JAVA_HOME naming the runtime: it loads the runtime's lib/libjli.so and hands it
 * the command line, which is what bin/java does. libjli finds the rest of the runtime beside itself.
 *
 * The runtime's libraries need the app's libc++_shared.so: start it with LD_LIBRARY_PATH holding
 * the native library folder.
 *
 * libjli starts its program again (re-exec) when LD_LIBRARY_PATH names the folder of libjvm.so but
 * does not begin with it, to set it the way it wants; Android's runtime builds do that through the
 * runtime's bin/java, which Android does not allow. So LD_LIBRARY_PATH is made to begin with that
 * folder (lib/server) first: libjli then has nothing to change and goes on in this process.
 */
#include <dlfcn.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* jdk/src/java.base/share/native/libjli/java.h */
typedef int (*JLI_Launch_t)(int argc, char **argv, int jargc, const char **jargv, int appclassc,
                            const char **appclassv, const char *fullversion, const char *dotversion,
                            const char *pname, const char *lname, unsigned char javaargs,
                            unsigned char cpwildcard, unsigned char javaw, int ergo_class);

int main(int argc, char **argv) {
    const char *home = getenv("JAVA_HOME");
    if (home == NULL || home[0] == '\0') {
        fprintf(stderr, "java: JAVA_HOME is not set\n");
        return 1;
    }
    char server[PATH_MAX];
    snprintf(server, sizeof server, "%s/lib/server", home);
    const char *old = getenv("LD_LIBRARY_PATH");
    if (old == NULL || strncmp(old, server, strlen(server)) != 0) {
        size_t size = strlen(server) + (old ? strlen(old) : 0) + 2;
        char *llp = malloc(size);
        if (llp == NULL) return 1;
        snprintf(llp, size, "%s%s%s", server, old && old[0] ? ":" : "", old ? old : "");
        setenv("LD_LIBRARY_PATH", llp, 1);
        free(llp);
    }
    char path[PATH_MAX];
    snprintf(path, sizeof path, "%s/lib/libjli.so", home);
    void *jli = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
    if (jli == NULL) {
        fprintf(stderr, "java: %s\n", dlerror());
        return 1;
    }
    JLI_Launch_t launch = (JLI_Launch_t) dlsym(jli, "JLI_Launch");
    if (launch == NULL) {
        fprintf(stderr, "java: no JLI_Launch in %s\n", path);
        return 1;
    }
    /* as bin/java: no built-in options, wildcards in -cp expanded */
    return launch(argc, argv, 0, NULL, 0, NULL, "25", "25", "java", "java", 0, 1, 0, 0);
}
