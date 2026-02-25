# espeak-ng-data

This folder must contain the eSpeak NG data files copied from the eSpeak NG source repository.

Copy them like this:

```bash
git clone https://github.com/espeak-ng/espeak-ng.git
cp -r espeak-ng/espeak-ng-data  app/src/main/assets/espeak-ng-data
```

The folder contains:
- `en_dict`, `ur_dict`, `fr_dict`, etc.  — compiled voice dictionaries
- `phondata`, `phonindex`, `phontab`      — phoneme tables
- `intonations`                           — intonation data
- `voices/`                               — voice definition files

On first app launch, SpeakEngine.kt automatically copies this entire folder
from the APK assets to the app's internal storage at:
  /data/data/com.example.speakng/files/espeak-ng-data/

This is necessary because eSpeak NG needs a real file path to read from.
