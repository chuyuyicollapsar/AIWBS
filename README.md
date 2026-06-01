# AIWBS

人工智能网文系统(Artificial Intelligence Web Book System)
AI 辅助写作工具 —— 基于 JavaFX 的桌面编辑器，集成多种大语言模型，提供智能写作辅助、章节管理和大纲编辑功能。

## 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Java 21 |
| UI 框架 | JavaFX 21 |
| AI 集成 | LangChain4j 1.13 (OpenAI / Anthropic) |
| 构建工具 | Maven (含 Maven Wrapper) |

## 环境要求

- **JDK 21** 或更高版本
- Maven 3.8+（可使用项目自带的 `mvnw`）

## 快速开始

```bash
# 克隆仓库
git clone https://github.com/chuyuyicollapsar/AIWBS.git
cd AIWBS

# 构建并运行
./mvnw javafx:run
```

首次运行时，需要在设置界面配置 AI 模型的 API Key。

## 功能

- 书籍管理（多卷、多章节、多版本）
- 大纲细纲管理
- AI对话（支持官方模型与第三方模型）
- 导入/导出打包

## 构建

```bash
# 编译
./mvnw clean compile

# 打包
./mvnw clean package

# 运行测试
./mvnw clean test
```

## 许可证

Copyright (C) 2024 AIWBS contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
