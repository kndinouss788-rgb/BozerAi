# BOZER NEURAL — Asset Register

Use one row per generated or manually created production asset. Keep the original file in the repository or record a durable external storage link.

| Asset ID | Type | Character / location / scene | Version | Source tool | File or URL | Duration | Status | Review notes |
|---|---|---|---|---|---|---|---|---|
| BRAND-BOZER-LOGO-001 | LOGO | BOZER IA — official symbol | v001 | Creator-supplied image | assets/branding/bozer-logo-master.jpg | — | APPROVED REFERENCE | Exact creator-selected spiral and segmented-rings mark; geometry and monochrome treatment are locked |
| — | — | — | — | — | — | — | NOT STARTED | No approved character, location, storyboard or animation assets registered yet |

## ID convention
- Brand / logo: `BRAND-[NAME]-LOGO-v001`
- Character reference: `CHR-[NAME]-REF-v001`
- Location reference: `LOC-[NAME]-REF-v001`
- Storyboard frame: `S01E01-SC01-SH001-BOARD-v001`
- Generated shot: `S01E01-SC01-SH001-VIDEO-v001`
- Dialogue audio: `S01E01-SC01-DLG-[CHARACTER]-v001`
- Final episode master: `S01E01-MASTER-v001`

## Required metadata for generated assets
- Asset ID and version
- Date created
- Tool / model and settings, if known
- Exact prompt or production instruction
- Input reference IDs
- Output path or URL
- Duration and aspect ratio (for video)
- Review status and reviewer notes
- Known defects / continuity risks

## Review statuses
`GENERATED` → `IN REVIEW` → `APPROVED` or `NEEDS REVISION`.

An output is not approved just because a tool generated it successfully.

## Logo preservation
The approved BOZER IA logo is documented in [BRAND_IDENTITY.md](BRAND_IDENTITY.md). Never alter the source geometry, colors or distinctive details. Any proposed alternate treatment must be presented separately and requires the creator's approval.
