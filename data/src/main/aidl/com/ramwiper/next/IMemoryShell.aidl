package com.ramwiper.next;
interface IMemoryShell {
    void destroy() = 16777114;
    int killBackground(String packageName, int userId) = 0;
    String memoryInfo(String packageName) = 1;
}
