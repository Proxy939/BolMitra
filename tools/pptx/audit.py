"""Extract every text run, picture and note from a .pptx for fact-checking."""
import sys
from pptx import Presentation
from pptx.util import Emu


def flatten(shapes):
    """Yield leaf shapes, descending into groups (grouped text is otherwise lost)."""
    for sh in shapes:
        if sh.shape_type is not None and 'GROUP' in str(sh.shape_type):
            yield from flatten(sh.shapes)
        else:
            yield sh


def dump(path):
    prs = Presentation(path)
    print(f"FILE: {path}")
    print(f"CANVAS: {Emu(prs.slide_width).inches:.2f} x "
          f"{Emu(prs.slide_height).inches:.2f} in")
    print(f"SLIDE COUNT: {len(prs.slides)}")

    for i, slide in enumerate(prs.slides, 1):
        print(f"\n{'='*72}")
        print(f"SLIDE {i}   layout='{slide.slide_layout.name}'")
        print('='*72)

        pics, texts = [], []
        for sh in flatten(slide.shapes):
            if sh.shape_type is not None and 'PICTURE' in str(sh.shape_type):
                dim = ""
                if sh.width and sh.height:
                    dim = (f" {Emu(sh.width).inches:.1f}x"
                           f"{Emu(sh.height).inches:.1f}in")
                pics.append(f"{sh.name}{dim}")
            if sh.has_text_frame:
                for p in sh.text_frame.paragraphs:
                    t = "".join(r.text for r in p.runs).strip()
                    if t:
                        texts.append(t)
            if getattr(sh, "has_table", False):
                for r_i, row in enumerate(sh.table.rows):
                    cells = [c.text.strip().replace("\n", " ")
                             for c in row.cells]
                    texts.append(f"[TABLE r{r_i}] " + " | ".join(cells))

        if pics:
            print(f"-- PICTURES ({len(pics)}): {', '.join(pics)}")
        print(f"-- TEXT RUNS ({len(texts)}):")
        for t in texts:
            print(f"   {t}")

        if slide.has_notes_slide:
            n = slide.notes_slide.notes_text_frame.text.strip()
            if n:
                print(f"-- NOTES: {n}")


if __name__ == "__main__":
    for p in sys.argv[1:]:
        dump(p)
