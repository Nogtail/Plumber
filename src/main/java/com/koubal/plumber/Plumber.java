package com.koubal.plumber;

import com.google.common.base.Charsets;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import difflib.DiffUtils;
import difflib.Patch;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.*;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Plumber {
	private static final String MC_VERSION = "1.8";

	private static boolean running = false;
	private static boolean isUnix = !System.getProperty("os.name").startsWith("Windows");
	private static GUI gui;
	private static int progress = 0;
	private static File jarLocation;
	private static File jacobe = new File(jarLocation, "jacobe");

	public static void main(String[] args) {
		gui = new GUI();
		gui.setVisible(true);
		gui.appendStatus("Welcome to Plumber version 1.1");
	}

	public static boolean isRunning() {
		return running;
	}

	public static void run() throws Exception {
		running = true;

		jarLocation = Paths.get(Plumber.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toFile().getParentFile();
		gui.appendStatus("Starting Plumber in " + jarLocation.toString());

		if (System.getProperty("os.name").startsWith("Mac")) {
			throw new RuntimeException("Sadly Mac OS is not supported at this time! Please run this on a Windows or Linux OS.");
		}
		updateProgress();

		try {
			runProcess("git --version");
		} catch (Exception e) {
			throw new RuntimeException("You do not appear to have Git installed! Please install Git to continue.\n" + e.getMessage());
		}
		updateProgress();

		File bukkit = new File(jarLocation, "Bukkit");
		if (!bukkit.exists()) {
			clone("https://hub.spigotmc.org/stash/scm/spigot/bukkit.git", bukkit);
		}
		updateProgress();

		File craftBukkit = new File(jarLocation, "CraftBukkit");
		if (!craftBukkit.exists()) {
			clone("https://hub.spigotmc.org/stash/scm/spigot/craftbukkit.git", craftBukkit);
		}
		updateProgress();

		File spigot = new File(jarLocation, "Spigot");
		if (!spigot.exists()) {
			clone("https://hub.spigotmc.org/stash/scm/spigot/spigot.git", spigot);
		}
		updateProgress();

		File buildData = new File(jarLocation, "BuildData");
		if (!buildData.exists()) {
			clone("https://hub.spigotmc.org/stash/scm/spigot/builddata.git", buildData);
		}
		updateProgress();

		if (!jacobe.exists()) {
			gui.appendStatus("Jacobe not found!");
			if (isUnix) { //I know there is a better way, but I suck with streams...
				File jacobeLinux = new File(jarLocation, "jacobe.linux.tar.gz");
				download("http://www.tiobe.com/content/products/jacobe/jacobe.linux.tar.gz", jacobeLinux);
				FileInputStream in = new FileInputStream(jacobeLinux);
				GzipCompressorInputStream gzIn = new GzipCompressorInputStream(in);
				TarArchiveInputStream tarIn = new TarArchiveInputStream(gzIn);

				for (TarArchiveEntry entry = tarIn.getNextTarEntry(); entry != null; entry = tarIn.getNextTarEntry()) {
					File destination = new File(jacobe, entry.getName());
					if (entry.isDirectory()) {
						destination.mkdirs();
						continue;
					}

					if (destination.getParentFile() != null) {
						destination.getParentFile().mkdirs();
					}

					byte[] buffer = new byte[4096];
					FileOutputStream out = new FileOutputStream(destination);

					for (int len = 0; (len = tarIn.read(buffer)) != -1;) {
						out.write(buffer, 0, len);
					}
				}
			} else {
				File jacobeWindows = new File(jarLocation, "jacobe.win32.zip");
				download("http://www.tiobe.com/content/products/jacobe/jacobe.win32.zip", jacobeWindows);
				unzip(jacobeWindows, jacobe);
			}
		}
		updateProgress();

		File maven = new File(jarLocation, "apache-maven-3.2.3");
		if(!maven.exists()) {
			gui.appendStatus("Maven does not exist, downloading now");
			File mavenTemp = new File(jarLocation, "maven.zip");
			mavenTemp.deleteOnExit();
			download("http://static.spigotmc.org/maven/apache-maven-3.2.3-bin.zip", mavenTemp);
			unzip(mavenTemp, jarLocation);
		}
		updateProgress();

		Git bukkitGit = Git.open(bukkit);
		pull(bukkitGit);
		updateProgress();

		Git craftBukkitGit = Git.open(craftBukkit);
		pull(craftBukkitGit);
		updateProgress();

		Git spigotGit = Git.open(spigot);
		pull(spigotGit);
		updateProgress();

		Git buildGit = Git.open(buildData);
		pull(buildGit);
		updateProgress();

		File work = new File(jarLocation, "work");
		if (!work.exists()) {
			work.mkdir();
		}
		updateProgress();

		File vanillaJar = new File(work, "minecraft_server." + MC_VERSION + ".jar");
		if (!vanillaJar.exists()) {
			download(String.format("https://s3.amazonaws.com/Minecraft.Download/versions/%1$s/minecraft_server.%1$s.jar", MC_VERSION), vanillaJar);
		}
		updateProgress();

		Iterable<RevCommit> mappings = buildGit.log()
				.addPath("mappings/bukkit-1.8.at")
				.addPath("mappings/bukkit-1.8-cl.csrg")
				.addPath("mappings/bukkit-1.8-members.csrg")
				.addPath("mappings/package.srg")
				.setMaxCount(1).call();

		Hasher mappingsHasher = Hashing.md5().newHasher();
		for (RevCommit rev : mappings) {
			mappingsHasher.putString(rev.getName(), Charsets.UTF_8);
		}
		String mappingsVersion = mappingsHasher.hash().toString().substring(24);

		File finalMappedJar = new File(work, "mapped." + mappingsVersion + ".jar");
		if (!finalMappedJar.exists()) {
			gui.appendStatus("Creating mapped jar");

			File clMappedJar = new File(finalMappedJar + "-cl");
			File mMappedJar = new File(finalMappedJar + "-m");

			runProcess("java -jar BuildData/bin/SpecialSource.jar -i " + vanillaJar + " -m BuildData/mappings/bukkit-1.8-cl.csrg -o " + clMappedJar);
			runProcess( "java -jar BuildData/bin/SpecialSource-2.jar map -i " + clMappedJar + " -m " + "BuildData/mappings/bukkit-1.8-members.csrg -o " + mMappedJar);
			runProcess("java -jar BuildData/bin/SpecialSource.jar -i " + mMappedJar + " --access-transformer BuildData/mappings/bukkit-1.8.at " + "-m BuildData/mappings/package.srg -o " + finalMappedJar);
		}
		updateProgress();

		String mavenCommand = maven.getAbsolutePath() + "/bin/mvn";
		if (isUnix) {
			mavenCommand = "/bin/sh " + mavenCommand; // Huh?
		} else {
			mavenCommand += ".bat";
		}

		gui.appendStatus("Installing mapped jar into Maven, a crash here usually indicates an unset JAVA_HOME");
		runProcess(mavenCommand + " install:install-file -Dfile=" + finalMappedJar + " -Dpackaging=jar -DgroupId=org.spigotmc -DartifactId=minecraft-server -Dversion=1.8-SNAPSHOT");
		updateProgress();

		File decompile = new File(work, "decompile-" + mappingsVersion);
		if (!decompile.exists()) {
			decompile.mkdir();

			File classes = new File(decompile, "classes");
			unzip(finalMappedJar, classes, "net/minecraft/server");
			runProcess("java -jar BuildData/bin/fernflower.jar -dgs=1 -hdc=0 -rbr=0 -asc=1 " + classes + " " + decompile);
		}
		updateProgress();

		String jacobePath = jacobe.getPath() + "/jacobe" + ((!isUnix) ? ".exe" : "");
		runProcess(jacobePath + " -cfg=BuildData/bin/jacobe.cfg -nobackup -overwrite -outext=java " + decompile + "/net/minecraft/server");
		updateProgress();

		File nms = new File(craftBukkit, "src/main/java/net");
		if (nms.exists()) {
			gui.appendStatus("Backing up NMS");
			FileUtils.moveDirectory(nms, new File(work, "nms.old." + System.currentTimeMillis()));
		}
		updateProgress();

		File patches = new File(craftBukkit, "nms-patches");
		for (File file : patches.listFiles()) {
			String target = "net/minecraft/server/" + file.getName().replaceAll(".patch", ".java");
			File clean = new File(decompile, target);
			File t = new File(nms.getParentFile(), target);
			t.getParentFile().mkdirs(); // Hmm, what is this?
			gui.appendStatus("Patching " + file.getName());

			Patch parsedPatch = DiffUtils.parseUnifiedDiff(Files.readLines(file, Charsets.UTF_8));
			List modifiedLines = DiffUtils.patch(Files.readLines(clean, Charsets.UTF_8), parsedPatch);

			BufferedWriter writer = new BufferedWriter(new FileWriter(t));
			for (String line : (List<String>) modifiedLines) {
				writer.write(line + "\n");
			}
			writer.close();
		}
		updateProgress();

		File tempNms = new File(craftBukkit, "tmp-nms");
		FileUtils.copyDirectory(nms, tempNms);

		craftBukkitGit.branchDelete().setBranchNames("patched").setForce(true).call();
		craftBukkitGit.checkout().setCreateBranch(true).setForce(true).setName("patched").call();
		craftBukkitGit.add().addFilepattern("src/main/java/net/").call();
		craftBukkitGit.commit().setMessage("CraftBukkit $ " + new Date()).call();
		craftBukkitGit.checkout().setName("master").call();

		FileUtils.moveDirectory(tempNms, nms);
		updateProgress();

		File spigotApi = new File(spigot, "Bukkit");
		if (!spigotApi.exists()) {
			clone("file://" + bukkit.getAbsolutePath(), spigotApi);
		}
		updateProgress();

		File spigotServer = new File( spigot, "CraftBukkit" );
		if(!spigotServer.exists()) {
			clone("file://" + craftBukkit.getAbsolutePath(), spigotServer);
		}
		updateProgress();

		gui.appendStatus("Compiling Bukkit");
		runProcess(mavenCommand + " clean install", bukkit);
		updateProgress();

		gui.appendStatus("Compiling CraftBukkit");
		runProcess(mavenCommand + " clean install", craftBukkit);
		updateProgress();

		if (isUnix) {
			runProcess("bash applyPatches.sh", spigot);
		} else {
			gui.appendStatus("I have detected you are using Windows. You will be punished!");
			gui.appendStatus("Launching patcher!");
			URL batchURL = Plumber.class.getResource("/applyPatches.bat");
			File destination = new File(spigot, "applyPatches.bat");
			FileUtils.copyURLToFile(batchURL, destination);
			runProcess("cmd /c applyPatches.bat", spigot);
		}
		updateProgress();

		gui.appendStatus("Compiling Spigot & Spigot-API");
		runProcess(mavenCommand + " clean install", spigot);
		gui.setProgress(100);
	}

	private static void clone(String url, File destination) throws GitAPIException{
		gui.appendStatus("Cloning " + url + " to " + destination);
		Git result = null;
		try {
			result = Git.cloneRepository().setURI(url).setDirectory(destination).call();
		} finally {
			if (result != null) {
				result.close();
				gui.appendStatus("Cloned " + url + " to " + destination);
			}
		}
	}

	private static void download(String urlString, File destination) throws IOException {
		gui.appendStatus("Starting download of " + urlString);
		URL url = new URL(urlString);
		FileUtils.copyURLToFile(url, destination);
		gui.appendStatus("Finished downloading " + urlString);
	}

	private static void unzip(File source, File destination) throws IOException {
		unzip(source, destination, null);
	}

	private static void unzip(File source, File destination, String filter) throws IOException{
		gui.appendStatus("Extracting " + source + " to " + destination);
		destination.mkdir();
		ZipFile zip = new ZipFile(source);

		Enumeration<? extends ZipEntry> entries = zip.entries();
		while (entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			if (filter != null) {
				if (!entry.getName().startsWith(filter)) {
					continue;
				}
			}

			gui.appendStatus("Extracting " + entry.getName());

			File out = new File(destination, entry.getName());

			if (entry.isDirectory()) {
				out.mkdirs();
				continue;
			}

			if (out.getParentFile() != null) {
				out.getParentFile().mkdirs();
			}

			InputStream inputStream = zip.getInputStream(entry);
			OutputStream outputStream = new FileOutputStream(out);

			try {
				ByteStreams.copy(inputStream, outputStream);
			} finally {
				inputStream.close();
				outputStream.close();
			}
		}

		gui.appendStatus("Finished extracting " + source);
	}

	private static void pull(Git repo) throws GitAPIException{
		gui.appendStatus("Updating " + repo.getRepository().getDirectory());
		repo.reset().setRef( "origin/master" ).setMode( ResetCommand.ResetType.HARD ).call();
		if (!repo.pull().call().isSuccessful()) {
			throw new RuntimeException("Could not update " + repo.getRepository().getDirectory());
		}

		gui.appendStatus("Updated " + repo.getRepository().getDirectory());
	}

	private static void runProcess(String command) throws IOException, InterruptedException {
		runProcess(command, jarLocation);
	}

	private static void runProcess(String command, File dir) throws IOException, InterruptedException {
		gui.appendStatus("Starting process " + command);
		final Process process = Runtime.getRuntime().exec(command, null, dir);

		new Thread(new Runnable() {
			@Override
			public void run() {
				BufferedReader inputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				try {
					String line;
					while ((line = inputReader.readLine()) != null) {
						gui.appendStatus(line);
					}
				} catch (IOException e) {
					throw new RuntimeException("Error reading process output!");
				}
			}
		}).start();

		new Thread(new Runnable() {
			@Override
			public void run() {
				BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
				try {
					String line;
					while ((line = errorReader.readLine()) != null) {
						gui.appendStatus(line);
					}
				} catch (IOException e) {
					throw new RuntimeException("Error reading process output!");
				}
			}
		}).start();

		int status = process.waitFor();
		if (status != 0) {
			throw new RuntimeException("Error running command! Status = " + status);
		}

		gui.appendStatus("Process finished");
	}

	private static void updateProgress() {
		gui.setProgress(++progress);
	}

	private static void applyPatch(File directory, File what, File target) throws Exception {
		Git git = Git.open(what);
		git.apply();
	}
}
