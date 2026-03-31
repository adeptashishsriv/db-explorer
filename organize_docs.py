#!/usr/bin/env python3
"""
Script to move all .md files to docs folder
"""
import os
import shutil
from pathlib import Path

# Get the project root
root_path = Path(__file__).parent

# Create docs folder if it doesn't exist
docs_path = root_path / "docs"
docs_path.mkdir(exist_ok=True)

# List of .md files to move
md_files = list(root_path.glob("*.md"))

# Move each file
moved_count = 0
for md_file in md_files:
    dest = docs_path / md_file.name
    try:
        shutil.move(str(md_file), str(dest))
        print(f"✅ Moved: {md_file.name}")
        moved_count += 1
    except Exception as e:
        print(f"❌ Error moving {md_file.name}: {e}")

print(f"\n✅ Successfully moved {moved_count} files to docs/ folder")

# List files in docs folder
print("\n📁 Files now in docs folder:")
for f in sorted(docs_path.glob("*.md")):
    print(f"   - {f.name}")

