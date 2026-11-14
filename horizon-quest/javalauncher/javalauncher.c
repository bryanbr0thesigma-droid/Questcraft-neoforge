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
 */
#include <dlfcn.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>

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
