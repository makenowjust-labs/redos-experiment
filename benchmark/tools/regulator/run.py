# Runs the driver of Regulator (`main.py`) with the arguments, and writes what its phases returned as JSON on the last
# line of the standard output.
# The driver itself writes only the length of the binary search, so the functions of its phases are replaced by ones
# that record their results.
import json
import math
import os

import binsearch_pump
import main
import pump

binary_search = os.environ.get("REGULATOR_BINARY_SEARCH") != "false"
result = {"times": []}

pump_full_report = pump.pump_full_report
get_pump_report = pump.get_pump_report
time_pump_async = binsearch_pump.WallTimeSampler.time_pump_async
find_limit = binsearch_pump.find_limit


class Validated(Exception):
    pass


def record_pump_full_report(*args):
    full = pump_full_report(*args)
    # A report of a time-out of the witness has no profiles.
    result["profiles"] = full[-1] if len(full) > 1 else []
    return full


def record_get_pump_report(pattern, flags, witness, width, deadline):
    report = get_pump_report(pattern, flags, witness, width, deadline)
    result["witness"] = witness.decode("latin1" if width == 1 else "utf-16-le", "surrogatepass")
    result["class"] = report["class"]
    if "pump_pos" in report:
        position = (report["pump_pos"], report["pump_len"])
        result["pump_pos"], result["pump_len"] = position
        if report["class"] == "POLYNOMIAL":
            # A class of the polynomial is `('POLYNOMIAL', r2, degree, coefficient, should_break)`.
            result["degree"] = next(
                int(profile[0][2])
                for profile in result["profiles"]
                if profile[0][0] == "POLYNOMIAL" and tuple(profile[1:3]) == position
            )
    return report


async def record_time_pump_async(self, num_pumps):
    time = await time_pump_async(self, num_pumps)
    result["times"].append([num_pumps, None if time == math.inf else time])
    # The first two measurements of the binary search are the ones of one pump and of the longest string.
    if not binary_search and len(result["times"]) == 2:
        raise Validated()
    return time


def record_find_limit(*args):
    result["length"] = find_limit(*args)
    return result["length"]


pump.pump_full_report = record_pump_full_report
pump.get_pump_report = record_get_pump_report
binsearch_pump.WallTimeSampler.time_pump_async = record_time_pump_async
binsearch_pump.find_limit = record_find_limit

try:
    main.main()
except Validated:
    pass
except SystemExit as exit:
    # The driver exits with 7 when the fuzzer wrote no witness in its time.
    if exit.code != 7:
        raise
    result["exit"] = 7

result.pop("profiles", None)
print(json.dumps(result))
