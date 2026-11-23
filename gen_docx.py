# -*- coding: utf-8 -*-
"""把 Markdown 转成 Word 文档。运行：python gen_docx.py [输入.md] [输出.docx]"""
import re
import sys
from docx import Document
from docx.shared import Pt, RGBColor, Cm
from docx.oxml.ns import qn

EAST_ASIA = "微软雅黑"


def set_font(run, bold=False, italic=False, color=None, size=None):
    run.font.name = "Calibri"
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = rpr.makeelement(qn("w:rFonts"), {})
        rpr.append(rfonts)
    rfonts.set(qn("w:eastAsia"), EAST_ASIA)
    run.bold = bold
    run.italic = italic
    if color is not None:
        run.font.color.rgb = color
    if size is not None:
        run.font.size = Pt(size)


def add_markdown_runs(paragraph, text):
    # 处理 **加粗** 语法：奇数段是加粗
    parts = text.split("**")
    for i, part in enumerate(parts):
        if part == "":
            continue
        run = paragraph.add_run(part)
        set_font(run, bold=(i % 2 == 1))


doc = Document()
normal = doc.styles["Normal"]
normal.font.name = "Calibri"
normal.font.size = Pt(11)
normal._element.rPr.rFonts.set(qn("w:eastAsia"), EAST_ASIA)

in_path = sys.argv[1] if len(sys.argv) > 1 else "docs/简历与面试发散.md"
out_path = sys.argv[2] if len(sys.argv) > 2 else "docs/简历与面试发散.docx"

with open(in_path, "r", encoding="utf-8") as f:
    lines = f.read().split("\n")

for line in lines:
    s = line.strip()
    if not s or s == "---":
        continue
    if s.startswith("> "):
        p = doc.add_paragraph()
        run = p.add_run(s[2:])
        set_font(run, italic=True, color=RGBColor(0x80, 0x80, 0x80))
    elif s.startswith("# "):
        p = doc.add_heading(level=0)
        add_markdown_runs(p, s[2:])
    elif s.startswith("## "):
        p = doc.add_heading(level=1)
        add_markdown_runs(p, s[3:])
    elif s.startswith("### "):
        p = doc.add_heading(level=2)
        add_markdown_runs(p, s[4:])
    elif re.match(r"^\d+\.\s", s):
        p = doc.add_paragraph(style="List Number")
        add_markdown_runs(p, re.sub(r"^\d+\.\s", "", s))
    elif s.startswith("- "):
        p = doc.add_paragraph(style="List Bullet")
        add_markdown_runs(p, s[2:])
    elif s.startswith("→ "):
        p = doc.add_paragraph()
        p.paragraph_format.left_indent = Cm(0.75)
        add_markdown_runs(p, s[2:])
    else:
        p = doc.add_paragraph()
        add_markdown_runs(p, s)

doc.save(out_path)
print("OK:", out_path)
