@echo off

set basedir=%cd%
echo Rebuilding Forked projects...

set what=Bukkit
set target=Spigot-API

cd "%basedir%/%what%"
git fetch
git reset --hard origin/patched
git branch -f upstream

cd "%basedir%"
if not exist "%basedir%/%target%" git clone %what% %target% -b upstream

cd "%basedir%/%target%"
echo Resetting %target% to %what%...
git remote rm upstream
git remote add upstream ../%what%
git checkout master
git fetch upstream
git reset --hard upstream/upstream

echo Applying patches to %target%...
git am --abort
for %%f in ("%basedir%/%what%-Patches/*.patch") do git am --3way "%basedir%/%what%-Patches/%%~nf.patch"

set what=CraftBukkit
set target=Spigot-Server

cd "%basedir%/%what%"
git fetch
git reset --hard origin/patched
git branch -f upstream

cd "%basedir%"
if not exist "%basedir%/%target%" git clone %what% %target% -b upstream

cd "%basedir%/%target%"
echo Resetting %target% to %what%...
git remote rm upstream
git remote add upstream ../%what%
git checkout master
git fetch upstream
git reset --hard upstream/upstream

echo Applying patches to %target%...
git am --abort
for %%f in ("%basedir%/%what%-Patches/*.patch") do git am --3way "%basedir%/%what%-Patches/%%~nf.patch"

cd %basedir%