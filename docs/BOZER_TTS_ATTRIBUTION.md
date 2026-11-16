# BOZER TTS — attribution de la voix française MLS

BOZER intègre le modèle vocal français **fr_FR-mls-medium** au format Sherpa-ONNX.

- Langue : français de France (fr_FR)
- Qualité indiquée par la fiche Piper : medium
- Corpus : Multilingual LibriSpeech (MLS), OpenSLR 94
- Licence du corpus/modèle indiquée sur sa fiche : CC BY 4.0
- Taille du checkpoint référencé par le miroir Sherpa : environ 76,7 Mo
- Le modèle contient 125 profils. BOZER utilise provisoirement le profil 55 ; son caractère masculin et son timbre doivent être confirmés par écoute avant de le figer.

Références :
- Fiche officielle Piper : https://huggingface.co/rhasspy/piper-voices/blob/main/fr/fr_FR/mls/medium/MODEL_CARD
- Fichiers Sherpa convertis : https://huggingface.co/XODalG/piper-voices-sherpa/tree/main/fr/fr_FR/mls/medium
- Métadonnées des 125 profils : https://huggingface.co/rhasspy/piper-voices/blob/main/voices.json
- Corpus MLS / OpenSLR 94 : https://openslr.org/94/
- Sherpa-ONNX : https://github.com/k2-fsa/sherpa-onnx
- Licence CC BY 4.0 : https://creativecommons.org/licenses/by/4.0/

Mention d'attribution :
« fr_FR-mls-medium — modèle vocal français dérivé du corpus Multilingual LibriSpeech (MLS), OpenSLR 94, sous licence CC BY 4.0. Conversion Sherpa-ONNX fournie par XODalG/piper-voices-sherpa. »

BOZER règle le profil de locuteur, le débit de parole, le niveau de bruit du générateur et les pauses. Ces réglages personnalisent la synthèse, mais ne réentraînent pas les poids et ne créent pas une voix originale BOZER. L'écoute sur des appareils réels est nécessaire avant toute affirmation sur la naturalité ou le genre du timbre.
