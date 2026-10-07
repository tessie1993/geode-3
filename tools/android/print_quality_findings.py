#!/usr/bin/env python3
"""Print findings into Actions logs so failures are reviewable without a ZIP."""
from pathlib import Path
import xml.etree.ElementTree as ET

for root in (Path('app/build/reports'), Path('engine')):
    for report in sorted(root.rglob('*.xml')):
        if '/build/reports/' not in str(report):
            continue
        try:
            tree = ET.parse(report)
        except ET.ParseError:
            continue
        for node in tree.iter('file'):
            for error in node.findall('error'):
                print(f"{node.get('name')}:{error.get('line')}: {error.get('source')} {error.get('message')}")
        for issue in tree.iter('issue'):
            if issue.get('severity') in ('Error', 'Fatal'):
                print(f"{report}: {issue.get('id')}: {issue.get('message')}")
