package com.moto.resolution.clone.shizuku;

interface ICommandService {
    String execute(in String[] argv) = 1;
    void destroy() = 16777114;
}
