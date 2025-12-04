# This rig is based on hybrid_rig.v6.py with modifications to how the scratchpad is managed.
# Instead of overwriting the scratchpad, the agent appends to it,
# which helps in retaining a history of thoughts and actions.

from typing import List, Tuple, Dict, Any

from agent_protocol import Agent, Step, Task
from agent_protocol.models import StepResult

from agent_protocol.scripts.execute_python_code import execute_python_code
from agent_protocol.scripts.run_shell_command import run_shell_command
from agent_protocol.scripts.read_file import read_file
from agent_protocol.scripts.write_file import write_file
from agent_protocol.scripts.list_directory import list_directory
from agent_protocol.scripts.search_file_content import search_file_content
from agent_protocol.scripts.replace_file_content import replace_file_content
from agent_protocol.scripts.glob_files import glob_files
from agent_protocol.scripts.web_fetch import web_fetch
from agent_protocol.scripts.save_memory import save_memory
from agent_protocol.scripts.write_todos import write_todos
from agent_protocol.scripts.google_web_search import google_web_search

# All the tools that the agent can use.
ALL_TOOLS = [
    execute_python_code,
    run_shell_command,
    read_file,
    write_file,
    list_directory,
    search_file_content,
    replace_file_content,
    glob_files,
    web_fetch,
    save_memory,
    write_todos,
    google_web_search,
]

# This is the agent's "mind" that will be passed between steps.
# It contains the current task, the scratchpad, and the tools available.
class AgentMind:
    def __init__(self, task: Task):
        self.task = task
        self.scratchpad: List[str] = []
        self.tools = ALL_TOOLS

    def append_to_scratchpad(self, content: str):
        self.scratchpad.append(content)

    def get_scratchpad_content(self) -> str:
        return "\n".join(self.scratchpad)

    def reset_scratchpad(self):
        self.scratchpad = []

    async def execute_tool(self, tool_name: str, **kwargs) -> Any:
        for tool in self.tools:
            if tool.__name__ == tool_name:
                return await tool(**kwargs)
        raise ValueError(f"Tool {tool_name} not found.")

async def handle_task(agent: Agent, task: Task):
    """
    The entry point for the agent. This function is called once per task.
    It creates an AgentMind and then calls the _process_next_step method.
    """
    mind = AgentMind(task)
    await _process_next_step(agent, mind)

async def _process_next_step(agent: Agent, mind: AgentMind):
    """
    This function is called repeatedly until the task is complete.
    It gets the next thoughts and actions from the agent, executes them,
    and then calls itself again.
    """
    # Create a step to show the agent's thoughts and actions.
    step = Step(task_id=mind.task.task_id, input=mind.get_scratchpad_content())
    step = await agent.db.create_step(task_id=mind.task.task_id, input=mind.get_scratchpad_content())

    # Get the agent's next thoughts and actions.
    # The prompt should encourage the agent to append to the scratchpad,
    # rather than overwriting it, for better history tracking.
    response = await agent.llm.chat_completion(
        messages=[
            {
                "role": "system",
                "content": f"""
You are an autonomous AI agent. You are given a task and you need to complete it.
You have access to the following tools: {mind.tools}.

The user will provide you with a task. You need to use the tools to complete the task.
You can use the 'write_todos' tool to manage your subtasks for complex queries.
After each tool execution, the output will be appended to your scratchpad.
Your scratchpad is a running log of your thoughts and actions.
You should use the information in your scratchpad to decide on your next action.
Always remember to append to your scratchpad when you have new thoughts or actions.
Do NOT overwrite your scratchpad.

Your current scratchpad is:
{mind.get_scratchpad_content()}
""",
            },
            {"role": "user", "content": mind.task.input},
        ],
        tools=mind.tools,
    )

    thoughts = response.choices[0].message.content
    tool_calls = response.choices[0].message.tool_calls

    # Append the agent's thoughts to the scratchpad.
    mind.append_to_scratchpad(f"Thought: {thoughts}")

    # If there are tool calls, execute them and append the output to the scratchpad.
    if tool_calls:
        for tool_call in tool_calls:
            tool_name = tool_call.function.name
            kwargs = {k: v for k, v in tool_call.function.arguments.items()}
            try:
                tool_output = await mind.execute_tool(tool_name, **kwargs)
                mind.append_to_scratchpad(f"Tool Output ({tool_name}): {tool_output}")
            except Exception as e:
                mind.append_to_scratchpad(f"Error executing tool {tool_name}: {e}")

    # If the agent thinks the task is complete, set the step result and return.
    if "FINAL ANSWER" in thoughts:
        step_result = StepResult(
            task_id=mind.task.task_id,
            step_id=step.step_id,
            output=thoughts.replace("FINAL ANSWER", "").strip(),
            is_last=True,
        )
        await agent.db.update_step(step_result)
        return

    # Otherwise, continue to the next step.
    await _process_next_step(agent, mind)

# Register the handle_task function with the agent.
Agent.on_task_handler(handle_task)
