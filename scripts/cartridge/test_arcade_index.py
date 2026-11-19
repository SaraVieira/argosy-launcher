import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

from generate_arcade_index import generate


class ArcadeIdentityTest(unittest.TestCase):
    def test_ambiguous_alias_including_unillustrated_game_never_gets_art(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            archive = base / "metadata.zip"
            with ZipFile(archive, "w") as out:
                out.writestr("Metadata.xml", """<LaunchBox>
                    <Game><DatabaseID>1</DatabaseID><Name>A Game</Name><Platform>Arcade</Platform></Game>
                    <Game><DatabaseID>2</DatabaseID><Name>A Sequel</Name><Platform>Arcade</Platform></Game>
                    <GameAlternateName><DatabaseID>2</DatabaseID><AlternateName>A Game</AlternateName></GameAlternateName>
                    <GameAlternateName><DatabaseID>1</DatabaseID><AlternateName>Original Game</AlternateName></GameAlternateName>
                    <GameImage><DatabaseID>1</DatabaseID><Type>Arcade - Marquee</Type><FileName>marquee.png</FileName></GameImage>
                    <GameImage><DatabaseID>1</DatabaseID><Type>Screenshot - Game Title</Type><FileName>title.png</FileName></GameImage>
                    <GameImage><DatabaseID>1</DatabaseID><Type>Arcade - Cabinet</Type><FileName>cabinet.png</FileName></GameImage>
                    </LaunchBox>""")
                out.writestr("Mame.xml", """<LaunchBox>
                    <MameFile><FileName>conflict</FileName><Name>A Game</Name></MameFile>
                    <MameFile><FileName>original</FileName><Name>Original Game</Name></MameFile>
                    </LaunchBox>""")
            generate(archive, base)
            self.assertEqual((base / "titles.tsv").read_text(), "original game\t1\n")
            self.assertEqual((base / "roms.tsv").read_text(), "original\t1\n")
            self.assertEqual((base / "images.tsv").read_text(), "1\tmarquee.png\t\t\n")


if __name__ == "__main__":
    unittest.main()
