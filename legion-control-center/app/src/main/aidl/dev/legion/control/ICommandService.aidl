package dev.legion.control;
interface ICommandService {
    String execute(in String command) = 1;
    void destroy() = 16777114;
}
