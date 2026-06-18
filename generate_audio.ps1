# PowerShell Script to generate Speech Synthesis audio for the architecture guide
Add-Type -AssemblyName System.Speech
$speak = New-Object System.Speech.Synthesis.SpeechSynthesizer

# Ensure Videos directory exists
$dir = "C:\Users\srira\airelease\Videos"
if (!(Test-Path $dir)) {
    New-Item -ItemType Directory -Force -Path $dir
}

$audioPath = "C:\Users\srira\airelease\Videos\architecture_audio.wav"
$speak.SetOutputToWaveFile($audioPath)

$text = @"
Antigravity Architecture and Code Generation Mechanics.
Antigravity is an agentic A.I. coding assistant designed to operate autonomously in partnership with developers. It bridges the gap between high-level reasoning models and local file and command execution.

Chapter 1. Core Architecture.
The architecture consists of a continuous agentic loop. When a user prompt is received, the Gemini Core LLM generates a plan and chooses tool calls. These are routed to the execution sandbox, which contains file tools like view file and write to file, shell commands via run command with user approval, web tools like read url content and search web, and sub-tasks via subagents. The execution output is fed back as updated context, looping until the goal is achieved.

Chapter 2. Interactive Code Generation Loop.
Antigravity does not write code in a single generation step. Instead, it works through a four-step agentic loop.
First, Context Assembly: Compiles the conversation history, open editor files, cursor position, and operating system specs.
Second, Planning and Tool Selection: The Gemini model determines what actions are required and calls appropriate tools.
Third, Execution and Feedback: The local sandbox runs the tools and returns results or errors.
Fourth, Self-Correction and Debugging: If a command fails or a code lint error is detected, the model inspects the error and applies corrections.

Chapter 3. Sandboxing and Safe Command Execution.
To keep your system secure, Antigravity operates within a sandboxed file boundary:
First, Scope Restriction: Write permissions are restricted to the active workspace folder. Direct access to system directories requires explicit permission requests.
Second, User Gatekeeping: All shell command proposals are queued in a pending state until you approve or reject them. No commands run without your authorization.

Chapter 4. Subagents and Collaborative Workspaces.
For large scale engineering tasks, Antigravity uses subagents:
First, Specialized Profiles: It can spawn a research subagent to inspect code, or a self subagent to build helper tools.
Second, Workspace Branching: Subagents work in inherited, shared, or branched isolated workspaces.
Third, High-Concurrency Messaging: Subagents notify the parent agent upon completion, resolving problems in parallel.

Chapter 5. Customizations and Plugin System.
Antigravity adapts its behavior using a dual-scoped customization system:
First, Global Settings: Houses universally applied rules and system behaviors.
Second, Project Settings: Manages workspace-specific rules and folder maps.
Third, Skills: Custom task plans loaded dynamically based on matching triggers.
Fourth, Plugins: Specialized bundles of tool wrappers like chrome devtools plugin and google antigravity SDK.
"@

$speak.Speak($text)
$speak.Dispose()
Write-Host "Audio generated successfully at $audioPath"
