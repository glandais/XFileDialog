package net.tomahawk;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class XFileDialogLoader {

	/**
	 * Computes the MD5 value of the input stream.
	 * 
	 * @param input
	 *            InputStream.
	 * @return Encrypted string for the InputStream.
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 */
	static String md5sum(InputStream input) throws IOException {
		BufferedInputStream in = new BufferedInputStream(input);

		try {
			MessageDigest digest = java.security.MessageDigest
					.getInstance("MD5");
			DigestInputStream digestInputStream = new DigestInputStream(in,
					digest);
			for (; digestInputStream.read() >= 0;) {

			}
			ByteArrayOutputStream md5out = new ByteArrayOutputStream();
			md5out.write(digest.digest());
			return md5out.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("MD5 algorithm is not available: "
					+ e);
		} finally {
			in.close();
		}
	}

	private static boolean contentsEquals(InputStream in1, InputStream in2)
			throws IOException {
		if (!(in1 instanceof BufferedInputStream)) {
			in1 = new BufferedInputStream(in1);
		}
		if (!(in2 instanceof BufferedInputStream)) {
			in2 = new BufferedInputStream(in2);
		}

		int ch = in1.read();
		while (ch != -1) {
			int ch2 = in2.read();
			if (ch != ch2) {
				return false;
			}
			ch = in1.read();
		}
		int ch2 = in2.read();
		return ch2 == -1;
	}

	/**
	 * Extracts and loads the specified library file to the target folder
	 * 
	 * @param libFolderForCurrentOS
	 *            Library path.
	 * @param libraryFileName
	 *            Library name.
	 * @param targetFolder
	 *            Target folder.
	 * @return
	 */
	private static boolean extractAndLoadLibraryFile(
			String nativeLibraryFilePath) {
		// temporary library folder
		String tempFolder = new File(System.getProperty("java.io.tmpdir"))
				.getAbsolutePath();
		// Include architecture name in temporary filename in order to avoid
		// conflicts
		// when multiple JVMs with different architectures running at the same
		// time
		String uuid = UUID.randomUUID().toString();
		String extractedLibFileName = String.format("xfiledialog-%s.dll", uuid);
		File extractedLibFile = new File(tempFolder, extractedLibFileName);

		try {
			// Extract a native library file into the target directory
			InputStream reader = XFileDialogLoader.class
					.getResourceAsStream(nativeLibraryFilePath);
			FileOutputStream writer = new FileOutputStream(extractedLibFile);
			try {
				byte[] buffer = new byte[8192];
				int bytesRead = 0;
				while ((bytesRead = reader.read(buffer)) != -1) {
					writer.write(buffer, 0, bytesRead);
				}
			} finally {
				// Delete the extracted lib file on JVM exit.
				extractedLibFile.deleteOnExit();

				if (writer != null) {
					writer.close();
				}
				if (reader != null) {
					reader.close();
				}
			}

			// Set executable (x) flag to enable Java to load the native library
			extractedLibFile.setReadable(true);
			extractedLibFile.setWritable(true, true);
			extractedLibFile.setExecutable(true);

			// Check whether the contents are properly copied from the resource
			// folder
			{
				InputStream nativeIn = XFileDialogLoader.class
						.getResourceAsStream(nativeLibraryFilePath);
				InputStream extractedLibIn = new FileInputStream(
						extractedLibFile);
				try {
					if (!contentsEquals(nativeIn, extractedLibIn)) {
						throw new RuntimeException(String.format(
								"Failed to write a native library file at %s",
								extractedLibFile));
					}
				} finally {
					if (nativeIn != null) {
						nativeIn.close();
					}
					if (extractedLibIn != null) {
						extractedLibIn.close();
					}
				}
			}
			return loadNativeLibrary(tempFolder, extractedLibFileName);
		} catch (IOException e) {
			System.err.println(e.getMessage());
			return false;
		}

	}

	/**
	 * Loads native library using the given path and name of the library.
	 * 
	 * @param path
	 *            Path of the native library.
	 * @param name
	 *            Name of the native library.
	 * @return True for successfully loading; false otherwise.
	 */
	private static synchronized boolean loadNativeLibrary(String path,
			String name) {
		File libPath = new File(path, name);
		if (libPath.exists()) {

			try {
				System.load(new File(path, name).getAbsolutePath());
				return true;
			} catch (UnsatisfiedLinkError e) {
				System.err.println(e);
				return false;
			}

		} else {
			return false;
		}
	}

	/**
	 * Loads XFileDialog native library using given path and name of the
	 * library.
	 * 
	 * @throws
	 */
	public static void loadLibrary(String libName) throws Exception {
		// Load the os-dependent library from the jar file
		String xfiledialogNativeLibraryPath = "/net/tomahawk/native/" + libName + ".dll";
		boolean hasNativeLib = hasResource(xfiledialogNativeLibraryPath);

		if (!hasNativeLib) {
			throw new Exception(String.format("No native library is found"));
		}

		// Try extracting the library from jar
		if (extractAndLoadLibraryFile(xfiledialogNativeLibraryPath)) {
			return;
		}
		return;
	}

	private static boolean hasResource(String path) {
		return XFileDialogLoader.class.getResource(path) != null;
	}

}
