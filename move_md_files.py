#!/usr/bin/env python3
"""
Move all .md files from root to docs folder
Run with: python move_md_files.py
"""

import os
import shutil
import sys
from pathlib import Path

def main():
    # Get the script's directory (project root)
    root_dir = Path(__file__).parent.absolute()
    docs_dir = root_dir / "docs"

    # Create docs directory if it doesn't exist
    docs_dir.mkdir(exist_ok=True)
    print(f"✅ Docs folder: {docs_dir}")

    # Find all .md files in root directory
    md_files = sorted(root_dir.glob("*.md"))

    if not md_files:
        print("❌ No markdown files found in root directory")
        return

    print(f"\n📄 Found {len(md_files)} markdown files to move:")

    moved_count = 0
    skipped_count = 0
    errors = []

    for md_file in md_files:
        dest_file = docs_dir / md_file.name

        # Check if file already exists in docs
        if dest_file.exists():
            print(f"⏭️  SKIP: {md_file.name} (already in docs)")
            skipped_count += 1
            continue

        try:
            shutil.copy2(md_file, dest_file)
            print(f"✅ COPY: {md_file.name} → docs/")
            moved_count += 1
        except Exception as e:
            error_msg = f"❌ ERROR: {md_file.name} - {str(e)}"
            print(error_msg)
            errors.append(error_msg)

    # Summary
    print(f"\n{'='*60}")
    print(f"SUMMARY:")
    print(f"  ✅ Copied: {moved_count}")
    print(f"  ⏭️  Skipped: {skipped_count}")
    if errors:
        print(f"  ❌ Errors: {len(errors)}")
        for error in errors:
            print(f"     {error}")
    print(f"{'='*60}")

    # List files in docs
    print(f"\n📁 Files now in docs/:")
    doc_files = sorted(docs_dir.glob("*.md"))
    for f in doc_files:
        size_kb = f.stat().st_size / 1024
        print(f"   - {f.name} ({size_kb:.1f} KB)")

    print(f"\n✅ Total files in docs: {len(doc_files)}")
    print(f"\n📂 Docs location: {docs_dir}")
    print(f"\n🚀 Next step: Open docs/README.md or docs/QUICK_START.md")

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n⚠️  Operation cancelled by user")
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ Fatal error: {e}")
        sys.exit(1)

