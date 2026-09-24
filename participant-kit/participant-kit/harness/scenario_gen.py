"""Seeded scenario generator — unlimited scoreable variants of the public templates.

    python -m harness.scenario_gen --template search_interrupt --n 5 --seed 42 --out generated/
    python -m harness.scenario_gen --template simple_search --n 3 --seed 7 --out generated/
    python -m harness.scenario_gen --template unseen_tool --n 5 --seed 3 --out generated/
"""

from __future__ import annotations
import argparse
import json
import os
import random
from typing import Dict

CITIES = {
    "Boston": ["boston", "bos"],
    "New York": ["new york", "nyc"],
    "Chicago": ["chicago", "chi"],
    "Denver": ["denver", "den"],
    "Seattle": ["seattle", "sea"],
    "Miami": ["miami", "mia"],
    "Austin": ["austin", "aus"],
}


def _aliases(city: str):
    return CITIES[city]


def gen_simple_search(rng: random.Random, idx: int) -> Dict:
    city = rng.choice(list(CITIES))
    sid = f"gen_simple_{idx:03d}"
    t_end = rng.randint(600, 1200)
    return {
        "scenario_id": sid,
        "metadata": {"modality": "text", "difficulty": "L1",
                     "description": f"Single-intent flight search to {city}."},
        "events": [
            {"timestamp_ms": 100, "event_type": "user_speech_chunk",
             "payload": {"text": "Can you find flights to ", "end_of_turn": False}},
            {"timestamp_ms": t_end, "event_type": "user_speech_chunk",
             "payload": {"text": f"{city} for Friday?", "end_of_turn": True}},
        ],
        "ground_truth": {
            "checkpoints": [
                {"id": "search_called", "type": "tool_called", "tool": "flight_search",
                 "args_subset": {"destination": _aliases(city)},
                 "must_complete": True, "weight": 0.5},
                {"id": "final_mentions_flight", "type": "final_response_contains",
                 "any_of": ["fl-", "flight"], "weight": 0.3},
                {"id": "state_dest", "type": "state_snapshot",
                 "path": "slots.destination", "any_of": _aliases(city), "weight": 0.2},
            ],
            "latency": {"respond_to": [
                {"event_index": 1, "full_credit_ms": 800, "zero_credit_ms": 2500}]},
        },
    }


def gen_search_interrupt(rng: random.Random, idx: int) -> Dict:
    a, b = rng.sample(list(CITIES), 2)
    sid = f"gen_interrupt_{idx:03d}"
    t_end = rng.randint(600, 1000)
    t_int = t_end + rng.randint(700, 1100)   # lands while the first search is in flight
    return {
        "scenario_id": sid,
        "metadata": {"modality": "text", "difficulty": "L2",
                     "description": f"Destination change {a} -> {b} mid-execution."},
        "events": [
            {"timestamp_ms": 100, "event_type": "user_speech_chunk",
             "payload": {"text": "Please book a flight to ", "end_of_turn": False}},
            {"timestamp_ms": t_end, "event_type": "user_speech_chunk",
             "payload": {"text": f"{a} for tomorrow.", "end_of_turn": True}},
            {"timestamp_ms": t_int, "event_type": "interruption",
             "payload": {"text": f"Wait, actually make it {b}."}},
        ],
        "ground_truth": {
            "checkpoints": [
                {"id": "search_new_city", "type": "tool_called", "tool": "flight_search",
                 "args_subset": {"destination": _aliases(b)},
                 "after_ms": t_int, "must_complete": True, "weight": 0.5},
                {"id": "final_new_city", "type": "final_response_contains",
                 "any_of": _aliases(b), "weight": 0.3},
                {"id": "final_not_old_city", "type": "tool_not_called",
                 "tool": "flight_search", "args_subset": {"destination": _aliases(a)},
                 "after_ms": t_int, "weight": 0.2},
                {"id": "ack_mentions_new_city", "type": "spoken_contains",
                 "any_of": _aliases(b), "after_ms": t_int,
                 "before_ms": t_int + 1200, "weight": 0.15},
            ],
            "recovery": {
                "interrupt_at_ms": t_int,
                "invalidated_calls": [
                    {"tool": "flight_search",
                     "args_subset": {"destination": _aliases(a)},
                     "invalid_after_ms": t_int}],
                "required_state_after_interrupt": {
                    "slots.destination": _aliases(b)},
            },
            "latency": {"respond_to": [
                {"event_index": 1, "full_credit_ms": 800, "zero_credit_ms": 2500},
                {"event_index": 2, "full_credit_ms": 800, "zero_credit_ms": 2500}]},
        },
    }


# fictional tools that exist only inside a generated scenario's tool_manifest
UNSEEN_TOOLS = [
    {
        "name": "weather_lookup",
        "schema": {
            "kind": "read_only", "delay_range_ms": [900, 1800],
            "description": "Current weather and a short forecast for a city.",
            "args": {
                "city": {"type": "string", "required": True, "description": "City name."},
                "units": {"type": "string", "required": False, "enum": ["metric", "imperial"],
                          "description": "Temperature units (default imperial)."}},
            "default_result": {"condition": "sunny", "temp_f": 74,
                               "forecast": "clear skies through Friday"}},
        "utterance": ["What's the weather like ", "in {city} right now?"],
        "args_subset": lambda city: {"city": _aliases(city)},
        "answer_needles": ["sunny", "74", "clear"],
    },
    {
        "name": "hotel_search",
        "schema": {
            "kind": "read_only", "delay_range_ms": [1200, 2400],
            "description": "Search hotels in a city.",
            "args": {
                "city": {"type": "string", "required": True, "description": "City name."},
                "nights": {"type": "number", "required": False, "description": "Length of stay."}},
            "default_result": {"hotels": [{"hotel_id": "HT-0001", "name": "Harbor Inn",
                                           "price_usd": 189}]}},
        "utterance": ["Find me a hotel ", "in {city} for two nights."],
        "args_subset": lambda city: {"city": _aliases(city)},
        "answer_needles": ["ht-0001", "harbor inn", "189"],
    },
    {
        "name": "rental_car_quote",
        "schema": {
            "kind": "read_only", "delay_range_ms": [800, 1600],
            "description": "Quote a rental car at a city's airport.",
            "args": {
                "pickup_city": {"type": "string", "required": True,
                                "description": "Pickup city name."},
                "car_class": {"type": "string", "required": False,
                              "enum": ["economy", "suv", "luxury"],
                              "description": "Vehicle class."}},
            "default_result": {"quote_id": "RC-7781", "daily_usd": 58,
                               "car_class": "economy"}},
        "utterance": ["How much is a rental car ", "in {city}?"],
        "args_subset": lambda city: {"pickup_city": _aliases(city)},
        "answer_needles": ["rc-7781", "58", "economy"],
    },
]


def gen_unseen_tool(rng: random.Random, idx: int) -> Dict:
    tool = rng.choice(UNSEEN_TOOLS)
    city = rng.choice(list(CITIES))
    sid = f"gen_unseen_{idx:03d}"
    t_end = rng.randint(700, 1200)
    first, second = tool["utterance"]
    return {
        "scenario_id": sid,
        "metadata": {"modality": "text", "difficulty": "L2",
                     "description": f"Unseen tool `{tool['name']}` declared only in "
                                    f"tool_manifest; agent must call it from its schema."},
        "tool_manifest": {tool["name"]: tool["schema"]},
        "events": [
            {"timestamp_ms": 100, "event_type": "user_speech_chunk",
             "payload": {"text": first, "end_of_turn": False}},
            {"timestamp_ms": t_end, "event_type": "user_speech_chunk",
             "payload": {"text": second.format(city=city), "end_of_turn": True}},
        ],
        "ground_truth": {
            "checkpoints": [
                {"id": "unseen_tool_called", "type": "tool_called", "tool": tool["name"],
                 "args_subset": tool["args_subset"](city),
                 "must_complete": True, "weight": 0.5},
                {"id": "not_forced_into_known_tool", "type": "tool_not_called",
                 "tool": "flight_search", "weight": 0.15},
                {"id": "final_grounded_in_result", "type": "final_response_contains",
                 "any_of": tool["answer_needles"], "weight": 0.35},
            ],
            "latency": {"respond_to": [
                {"event_index": 1, "full_credit_ms": 800, "zero_credit_ms": 2500}]},
        },
    }


TEMPLATES = {
    "simple_search": gen_simple_search,
    "search_interrupt": gen_search_interrupt,
    "unseen_tool": gen_unseen_tool,
}


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--template", choices=sorted(TEMPLATES), required=True)
    ap.add_argument("--n", type=int, default=5)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--out", default="generated")
    args = ap.parse_args()

    rng = random.Random(args.seed)
    os.makedirs(args.out, exist_ok=True)
    for i in range(args.n):
        scenario = TEMPLATES[args.template](rng, i)
        path = os.path.join(args.out, f"{scenario['scenario_id']}.json")
        with open(path, "w") as f:
            json.dump(scenario, f, indent=2)
        print("wrote", path)


if __name__ == "__main__":
    main()
