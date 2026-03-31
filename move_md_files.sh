#!/bin/bash

# Move all .md files to docs folder

PROJECT_ROOT="C:\Users\ashis\IdeaProjects\db-explorer"
cd "$PROJECT_ROOT" || exit 1

# Create docs folder if needed
mkdir -p docs

count=0
skip=0

echo ""
echo "===================================================="
echo "Moving markdown files to docs folder..."
echo "===================================================="
echo ""

# Move all .md files
for file in *.md; do
    if [ -f "$file" ]; then
        dest="docs/$file"
        if [ -f "$dest" ]; then
            echo "[SKIP] $dest already exists"
            ((skip++))
        else
            echo "[MOVE] $file → docs/"
            mv "$file" "$dest"
            ((count++))
        fi
    fi
done

echo ""
echo "===================================================="
echo "Results:"
echo "  Moved:  $count files"
echo "  Skipped: $skip files"
echo "===================================================="
echo ""

# List files in docs folder
echo "Files in docs folder:"
echo ""
ls -1 docs/*.md 2>/dev/null | xargs -I {} basename {}

echo ""
echo "✅ Done! All markdown files are now in the docs/ folder"
echo ""
echo "📂 Location: $PROJECT_ROOT/docs/"
echo "📖 Start with: docs/README.md or docs/QUICK_START.md"
echo ""

