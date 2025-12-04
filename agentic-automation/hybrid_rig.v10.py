import os
import sys
from typing import TypedDict, Annotated, List
from langgraph.graph import StateGraph, END

# --- 1. SETUP THE BRAIN & HANDS ---
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_core.messages import SystemMessage, HumanMessage, BaseMessage

# "The Brain" = Gemini 3.0 Pro (The new flagship)
# NOTE: In Nov 2025, the API ID is "gemini-3-pro-preview"
brain = ChatGoogleGenerativeAI(model="gemini-3-pro-preview", temperature=0.2)

# "The Hands" = Gemini 2.5 Flash (The fast, cheap workhorse)
# NOTE: The API ID is "gemini-2.5-flash"
hands = ChatGoogleGenerativeAI(model="gemini-2.5-flash", temperature=0.0)

# --- 2. DEFINE THE STATE (THE CLIPBOARD) ---
class AgentState(TypedDict):
    mission: str            # The goal (e.g., "Add arrow keys")
    plan: str               # The Brain's breakdown of how to do it
    code: str               # The code written by the Hands
    error: str              # What you (the human) said went wrong
    review_count: int       # To prevent infinite loops
    messages: List[BaseMessage] # Chat history

# --- 3. DEFINE THE WORKERS (NODES) ---

def planner_node(state: AgentState):
    """The Brain analyzes the mission and error to create a plan."""
    print(f"\n🧠 BRAIN: Analyzing Mission... (Errors: {state.get('error', 'None')})")
    
    prompt = f"""
    You are the Architect.
    MISSION: {state['mission']}
    LAST ERROR (if any): {state.get('error', 'None')}
    
    Your goal is to create a step-by-step coding plan for the Junior Coder.
    Include specific instructions on libraries, syntax (NO XML), and logic.
    If there was an error, explain exactly how to fix it in the plan.
    Output ONLY the plan.
    """
    response = brain.invoke([HumanMessage(content=prompt)])
    return {"plan": response.content}

def coder_node(state: AgentState):
    """The Hands write the code based on the plan."""
    print("\n✍️ HANDS: Writing code...")
    
    prompt = f"""
    You are the Junior Coder. 
    PLAN: {state['plan']} 
    
    Write the complete file content necessary to execute this plan.
    Output ONLY the code. Do not use Markdown blocks (```). Just the raw code.
    """
    response = hands.invoke([HumanMessage(content=prompt)])
    # Strip markdown if the model forgets and adds it
    clean_code = response.content.replace("```python", "").replace("```", "").strip()
    return {"code": clean_code}

def reviewer_node(state: AgentState):
    """The Brain reviews the code BEFORE the human sees it."""
    print("\n🧐 BRAIN: Reviewing code for 'Laziness'...")
    
    prompt = f"""
    Review this code against the plan.
    PLAN: {state['plan']}
    CODE:
    {state['code']}
    
    Did the coder implement the logic correctly? 
    Did they just hardcode a result or suppress errors?
    If BAD, output: REJECT: [Reason]
    If GOOD, output: APPROVE
    """
    response = brain.invoke([HumanMessage(content=prompt)])
    
    if "REJECT" in response.content:
        print(f"❌ REVIEW FAILED: {response.content}")
        return {"error": f"Brain Rejected: {response.content}"} # Loops back to Planner
    else:
        print("✅ REVIEW PASSED.")
        return {"error": None} # Proceeds to Human

def human_node(state: AgentState):
    """The Human (You) applies the code and tests it."""
    filename = "generated_code.py" # You can make this dynamic later
    with open(filename, "w") as f:
        f.write(state['code'])
    
    print(f"\n💾 SYSTEM: Code saved to {filename}")
    print("-------------------------------------------------")
    print("🛑 HUMAN INTERVENTION REQUIRED")
    print("1. Read the code.")
    print("2. Run your build/test.")
    print("-------------------------------------------------")
    
    user_input = input("Did it work? (Type 'yes' or paste the error): ")
    
    if user_input.lower() in ["yes", "y", "good", "ok"]:
        return {"error": "SUCCESS"}
    else:
        return {"error": user_input}

# --- 4. BUILD THE GRAPH (THE CIRCUIT BOARD) ---
workflow = StateGraph(AgentState)

workflow.add_node("planner", planner_node)
workflow.add_node("coder", coder_node)
workflow.add_node("reviewer", reviewer_node)
workflow.add_node("human", human_node)

# Set the Entry Point
workflow.set_entry_point("planner")

# Define the Logic Flow
workflow.add_edge("planner", "coder")
workflow.add_edge("coder", "reviewer")

# Conditional Logic: If Review Fails, go back to Planner. If Passes, go to Human.
def check_review(state):
    if state.get("error"):
        return "planner"
    return "human"

workflow.add_conditional_edges("reviewer", check_review)

# Conditional Logic: If Human says Success, End. If Error, go back to Planner.
def check_human(state):
    if state['error'] == "SUCCESS":
        return END
    return "planner"

workflow.add_conditional_edges("human", check_human)

app = workflow.compile()

# --- 5. RUN THE RIG ---
if __name__ == "__main__":
    print(">>> HYBRID RIG v10 (Powered by LangGraph) <<<")
    mission = input("Enter Mission: ")
    initial_state = {"mission": mission, "error": None, "review_count": 0}
    
    # Run the graph until it hits END
    for output in app.stream(initial_state):
        pass # The nodes handle the printing
    
    print("\n🎉 MISSION ACCOMPLISHED.")
