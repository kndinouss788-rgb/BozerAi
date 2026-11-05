"""Turn parsed intents into proposals; never execute device actions here."""

from .models import ActionProposal, Intent, IntentKind
from .safety import is_actionable, requires_confirmation


def plan_action(intent: Intent) -> ActionProposal:
    if not is_actionable(intent):
        return ActionProposal(
            action="chat",
            arguments={"text": intent.raw_text},
            requires_confirmation=False,
            required_permission=None,
            safe_to_execute=False,
            explanation="No sufficiently explicit supported device action was found.",
        )

    permission = {
        IntentKind.SET_ALARM: "SCHEDULE_EXACT_ALARM",
        IntentKind.OPEN_APP: None,
        IntentKind.SEND_MESSAGE: "SEND_SMS_OR_MESSAGING_APP",
        IntentKind.MAKE_CALL: "CALL_PHONE",
    }.get(intent.kind)

    arguments = {}
    if intent.target:
        arguments["target"] = intent.target
    if intent.time_text:
        arguments["time"] = intent.time_text

    return ActionProposal(
        action=intent.kind.value,
        arguments=arguments,
        requires_confirmation=requires_confirmation(intent),
        required_permission=permission,
        safe_to_execute=True,
        explanation=(
            "Proposal only: Android must validate permissions, target, and confirmation "
            "before any device-side execution."
        ),
    )
