package dev.legion.control;
interface ICommandService {
    String execute(in String command) = 1;
    /** Read-only Android system eligibility test, no permission modification. */
    String queryAccessibilityTargets(in String[] packages, in int[] uids, int userId) = 2;
    void destroy() = 16777114;
}
