"""Can a 2-tall citizen walk from the hut block to the departure point? BFS over standable
cells: solid block below, two passable cells (air, doors, ladders, glass panes are NOT passable)
step up 1, drop down up to 3."""
from collections import deque
from voxel import parse_state

PASSABLE_WORDS = ("_door", "air", "chorus_flower")   # doors get opened; flowers are nothing to a colonist


def passable(s, x, y, z):
    b = s.get(x, y, z)
    if b is None:
        return True
    name = parse_state(b)[0]
    return any(w in name for w in PASSABLE_WORDS)


def solid(s, x, y, z):
    b = s.get(x, y, z)
    if b is None:
        return False
    name = parse_state(b)[0]
    return not any(w in name for w in ("_door", "chorus", "torch", "lantern", "end_rod", "lightning_rod", "iron_bars", "chain"))


def standable(s, x, y, z):
    return solid(s, x, y - 1, z) and passable(s, x, y, z) and passable(s, x, y + 1, z)


def reachable(s, start, goal_adjacent):
    """BFS from start; returns the set of visited cells and whether any cell next to goal was reached."""
    seen = {start}
    q = deque([start])
    gx, gy, gz = goal_adjacent
    while q:
        x, y, z = q.popleft()
        if abs(x - gx) + abs(z - gz) <= 1 and abs(y - gy) <= 1:
            return True, seen
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            for ny in (y + 1, y, y - 1, y - 2, y - 3):
                if (nx, ny, nz) in seen:
                    continue
                if standable(s, nx, ny, nz):
                    # stepping up needs headroom at the current cell too
                    if ny == y + 1 and not passable(s, x, y + 2, z):
                        continue
                    seen.add((nx, ny, nz))
                    q.append((nx, ny, nz))
                    break
    return False, seen


if __name__ == "__main__":
    import designs
    for lv in range(1, 6):
        for fn in (designs.launchpad, designs.endgate):
            st = fn(lv)
            dep = [p for p, ts in st.tags.items() if "departure" in ts][0]
            ok, seen = reachable(st, dep, st.anchor)
            print(f"{st.name}: departure {dep} standable={standable(st, *dep)} -> hut {st.anchor}: {'REACHABLE' if ok else 'NOT reachable'} ({len(seen)} cells explored)")
