#!/usr/bin/env python3
"""Update legacy v2.5 structural tests to match safer v2.6 arrivals."""
from pathlib import Path
path = Path(__file__).resolve().with_name('check_arrival_invariants.py')
text = path.read_text(encoding='utf-8')
replacements = [
    ("'ArrivalController.handleArrival(context, trigger == null || trigger.isFromMockProvider())' in receiver", "'ArrivalController.handleArrival(context, trigger.isFromMockProvider())' in receiver"),
    ("'if (!inside && outside) ArrivalController.handleArrival(this, mock)' in monitor", "'if (!inside && outside && consecutiveInside >= 2)' in monitor"),
]
for old, new in replacements:
    if new in text:
        continue
    if text.count(old) != 1:
        raise AssertionError('Expected unique old assertion: ' + old)
    text = text.replace(old, new, 1)
path.write_text(text, encoding='utf-8')
print('v2.6 structural test expectations updated')
