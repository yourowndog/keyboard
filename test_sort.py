import functools

def compare_attributes(attrs1, attrs2):
    if len(attrs1) == 0 and len(attrs2) == 0: return 0
    size_diff = (len(attrs1) > len(attrs2)) - (len(attrs1) < len(attrs2))
    if size_diff != 0: return size_diff
    
    keys1 = sorted(list(attrs1.keys()))
    keys2 = sorted(list(attrs2.keys()))
    
    for i in range(min(len(keys1), len(keys2))):
        k1 = keys1[i]
        k2 = keys2[i]
        if k1 != k2:
            return (k1 > k2) - (k1 < k2)
        
        vals1 = attrs1[k1]
        vals2 = attrs2[k2]
        for j in range(min(len(vals1), len(vals2))):
            v1 = vals1[j]
            v2 = vals2[j]
            if v1 != v2:
                return (v1 > v2) - (v1 < v2)
    return 0

def compare_rules(r1, r2):
    # r1, r2 = (elementName, selector, attributes_dict)
    if r1[0] != r2[0]:
        return (r1[0] > r2[0]) - (r1[0] < r2[0])
        
    s1 = r1[1]
    s2 = r2[1]
    if s1 != "NONE" or s2 != "NONE":
        if s1 == "NONE": return -1
        if s2 == "NONE": return 1
        if s1 != s2: return (s1 > s2) - (s1 < s2)
        
    return compare_attributes(r1[2], r2[2])

rules = [
    ("key", "NONE", {"code": ["-305", "-306"]}),
    ("key", "NONE", {"code": ["-305"], "numberrowstate": ["active"]}),
    ("key", "NONE", {"code": ["-306"], "devrowstate": ["active"]}),
    ("key", "NONE", {}),
    ("key", "PRESSED", {})
]

sorted_rules = sorted(rules, key=functools.cmp_to_key(compare_rules))
for r in sorted_rules:
    print(f"{r[0]}{r[2]} {r[1]}")
