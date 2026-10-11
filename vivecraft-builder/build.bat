echo Cloning Vivecraft and checkout
git clone --recursive https://github.com/Vivecraft/VivecraftMod.git
cd VivecraftMod
git checkout %BRANCH%

echo Patching...
git apply ..\QCXR.patch

echo Starting Build...
gradlew.bat build

echo Done!

echo Returning to directory.
cd ..
