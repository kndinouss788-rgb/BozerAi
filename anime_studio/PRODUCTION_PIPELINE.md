# BOZER NEURAL — Production Pipeline

## Goal
Create a repeatable studio workflow that can produce connected episodes, not disconnected AI clips.

## Stage A — Story room
For every episode:
1. Write a one-sentence premise.
2. Define the emotional question and the episode's dramatic change.
3. Create a beat sheet (opening, inciting event, escalation, turn, climax, ending hook).
4. Check against the series bible and previous episode continuity.
5. Obtain creator approval before visual production.

**Deliverable:** approved episode outline.

## Stage B — Script
1. Write scenes in order with INT./EXT., location and time.
2. Write action in filmable terms.
3. Write natural dialogue with distinct character voices.
4. Track entrances, exits, props, injuries, clothing and emotional state.
5. Read dialogue aloud / estimate runtime.
6. Approve the script.

**Deliverable:** locked episode screenplay.

## Stage C — Character and location design
For each recurring character, maintain:
- identity anchor (face, hair, eyes, build, posture);
- approved front / side / back views;
- expressions and emotional range;
- outfit variants with story-specific reasons;
- voice and speech patterns;
- transformation states and the episode where each begins.

For each recurring location, maintain:
- wide establishing view;
- layout and key landmarks;
- lighting / time-of-day variants;
- recurring props and background details.

**Deliverable:** approved character and location reference pack.

## Stage D — Storyboard and shot plan
For each scene:
- scene purpose;
- shot number;
- shot size and camera angle;
- character positions and screen direction;
- action and facial expression;
- dialogue / sound cue;
- start and end state;
- reference assets required.

**Deliverable:** storyboard and shot manifest.

## Stage E — Image and video production
- Generate or draw approved keyframes first.
- Use the same approved character references for every related shot.
- Animate only after keyframes are accepted.
- Produce short shots that can be edited together.
- Record tool, prompt, reference IDs, output URL, duration, aspect ratio and review status.
- Regenerate failed shots; never silently accept identity drift.

**Deliverable:** reviewed shot assets.

## Stage F — Voice, dialogue and sound
- Record or synthesize dialogue only after script approval.
- Keep one stable voice identity per character.
- Lip-sync is a separate production step; verify timing and mouth movement.
- Add ambience and effects that support the scene.
- Music is optional and must not block production; sound design can be used without a music track.

**Deliverable:** synchronized dialogue and sound stems.

## Stage G — Editing and continuity review
Check:
- scene order and pacing;
- character identity and clothing;
- geography, screen direction and eyelines;
- dialogue timing and lip-sync;
- lighting and color continuity;
- sound levels;
- story-bible consistency;
- opening and ending transitions.

**Deliverable:** episode assembly candidate.

## Stage H — Creator approval and archive
An episode becomes **APPROVED** only after creator review. Archive:
- final video master;
- final script;
- storyboard;
- shot manifest;
- approved references;
- tool/model and prompt notes;
- known defects and revision history.

## Production asset statuses
IDEA → SCRIPT DRAFT → SCRIPT APPROVED → DESIGN → STORYBOARD APPROVED → GENERATING → REVIEW → EDITING → CREATOR APPROVAL → APPROVED.

## Tool roles (current working assumptions)
- GitHub: versioned source of truth for canon, scripts, shot lists and production records.
- Runway: image production is currently available in the connected workspace; video generation is currently unavailable on its free workspace.
- Anime Style: convert existing short clips or images to anime style; it is not a full series studio.
- fal.ai: candidate workflow for character consistency and multi-shot production; operational access and pricing still need to be verified before depending on it.
- Sync Labs: candidate for lip-sync / dubbing; connection and pricing must be verified before production use.

No tool is treated as a complete animation studio by itself. The pipeline coordinates specialized tools while GitHub preserves continuity and project history.
