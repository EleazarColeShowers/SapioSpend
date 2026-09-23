# Enum constant names are persisted verbatim and read back by matching on them:
# SettingsRepository stores CheckInCadence.name in SharedPreferences, the Room
# `frequency` column stores Recurrence.name, BudgetAlertStore keys on
# BudgetThreshold.name, LocalEntitlements stores Plan.name, and FxRates caches
# Source.name. R8 is otherwise free to rename those constants, which would orphan
# every value already written to disk on an installed device.
-keepclassmembers enum * {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
