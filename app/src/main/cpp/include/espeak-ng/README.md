# include/espeak-ng/

Place the eSpeak NG public headers here:

- `espeak_ng.h`  — main API: espeak_ng_Initialize, espeak_ng_Terminate, etc.
- `speak_lib.h`  — synthesis API: espeak_Synth, espeak_SetParameter, etc.

Copy them from the eSpeak NG source:
```
espeak-ng/src/include/espeak-ng/espeak_ng.h  →  include/espeak-ng/espeak_ng.h
espeak-ng/src/include/espeak-ng/speak_lib.h  →  include/espeak-ng/speak_lib.h
```
