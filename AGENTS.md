# AGENTS.md - MobileJoystickControl 模组说明

## 文件结构（当前仓库）
```text
MobileJoystickControl/
|-- .github/
|-- assets/
|   \-- sprites/
|       |-- mjc-button-boost.png
|       |-- mjc-button-fire.png
|       |-- mjc-stick-base.png
|       \-- mjc-stick-knob.png
|-- gradle/
|   \-- wrapper/
|       |-- gradle-wrapper.jar
|       \-- gradle-wrapper.properties
|-- src/
|   \-- mobilejoystickcontrol/
|       \-- MobileJoystickControlMod.java
|-- .gitignore
|-- AGENTS.md
|-- build.gradle
|-- c57a87c3ab333bfa48fa4775136ec255.jpeg
|-- gradle.properties
|-- gradlew
|-- gradlew.bat
|-- mod.hjson
\-- README.md
```

## 维护约束
- 保持 Java 8 兼容（如本项目包含 Java 源码）。
- 变更优先聚焦性能与可读性，不做无关重构。
- 用户可见文案优先走 bundle/资源文件，不硬编码。

命令操作请使用 PowerShell 7（`pwsh`）。
