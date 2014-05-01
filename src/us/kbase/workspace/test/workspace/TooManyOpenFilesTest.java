package us.kbase.workspace.test.workspace;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class TooManyOpenFilesTest {
	private static final int FILE_COUNT = 50000;
	
	@Test
	public void mainTest() throws Exception {
		File dir = new File("temp_files");
		if (!dir.exists())
			dir.mkdir();
		List<File> files = new ArrayList<File>();
		try {
			for (int i = 0; i < FILE_COUNT; i++) {
				File f = File.createTempFile("too.many", ".json", dir);
				files.add(f);
				Writer w = new FileWriter(f);
				w.write("" + i);
				w.close();
			}
			for (File f : files) {
				InputStream is = new FileInputStream(f);
				byte[] buffer = new byte[10000];
				while (true) {
					int len = is.read(buffer);
					if (len < 0)
						break;
				}
				is.close();
			}
		} finally {
			for (File f : files)
				try {
					if (f.exists())
						f.delete();
				} catch (Exception ignore) {}
		}
	}
}
