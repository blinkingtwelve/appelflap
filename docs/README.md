# Appelflap documentation

## API documentation

See [here](API/HTTP_API.md).

## Debugging tips

### Webserver logs

The internal webserver writes some request info to standard error. This is captured in the Android system log,
and you can live-view these logs with
```
adb logcat System.err:W | egrep '(HttpLogger|PublicationHandler).*(REQUEST|PUBLICATIONTARGET):'
```
or the equivalent on your system.

### Advanced debugging tricks

There's a manhole of sorts, enabled in debug mode, that gives convenient access to Appelflap's innards.
Read the [Jefferies Tube http handler code](../eekhoorn/src/main/java/org/nontrivialpursuit/eekhoorn/httphandlers/JefferiesTube.kt) to see how to use it.