package org.rauschig.jarchivelib;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;

/**
 * Implementation of an {@link Archiver} that uses {@link ArchiveStreamFactory} to generate archive streams by a given
 * archiver name passed when creating the {@code GenericArchiver}. Thus, it can be used for all archive formats the
 * {@code org.apache.commons.compress} library supports.
 */
class GenericArchiver implements Archiver {

    private ArchiveStreamFactory streamFactory = new ArchiveStreamFactory();

    private final String archiverName;
    private final String fileExtension;

    GenericArchiver(String archiverName) {
        this.archiverName = archiverName.toLowerCase();
        this.fileExtension = "." + archiverName.toLowerCase();
    }

    /**
     * Returns the name of the archiver.
     * 
     * @see ArchiverFactory
     */
    public String getArchiverName() {
        return archiverName;
    }

    /**
     * Returns the file extension, which is equal to "." + {@link #getArchiverName()}
     */
    public String getFileExtension() {
        return fileExtension;
    }

    @Override
    public File create(String archive, File destination, File... sources) throws IOException {
        IOUtils.requireDirectory(destination);

        File archiveFile = createNewArchiveFile(archive, fileExtension, destination);

        try (ArchiveOutputStream outputStream = createArchiveOutputStream(archiveFile)) {

            writeToArchive(sources, outputStream);

            outputStream.flush();
        } catch (ArchiveException e) {
            throw new IOException(e);
        }

        return archiveFile;
    }

    @Override
    public void extract(File archive, File destination) throws IOException {
        IOUtils.requireDirectory(destination);

        try (ArchiveInputStream input = createArchiveInputStream(archive)) {

            ArchiveEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                File file = new File(destination, entry.getName());

                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    IOUtils.copy(input, file);
                }
            }

        } catch (ArchiveException e) {
            throw new IOException(e);
        }
    }

    /**
     * Uses the {@link #streamFactory} and the {@link #archiverName} to create a new {@link ArchiveOutputStream} for the
     * given archive {@link File}.
     * 
     * @param archive the archive file to create the {@link ArchiveOutputStream} for
     * @return a new {@link ArchiveOutputStream}
     * @throws IOException
     * @throws ArchiveException if the archiver name is not known
     */
    protected ArchiveOutputStream createArchiveOutputStream(File archive) throws IOException, ArchiveException {
        return streamFactory.createArchiveOutputStream(archiverName, new FileOutputStream(archive));
    }

    /**
     * Uses the {@link #streamFactory} to create a new {@link ArchiveInputStream} for the given archive file.
     * 
     * @param archive
     * @return
     * @throws IOException
     * @throws ArchiveException if the archiver name is not known
     */
    protected ArchiveInputStream createArchiveInputStream(File archive) throws IOException, ArchiveException {
        return streamFactory.createArchiveInputStream(new BufferedInputStream(new FileInputStream(archive)));
    }

    /**
     * Creates a new File in the given destination. The resulting name will always be "archive"."fileExtension". If the
     * archive name parameter already ends with the given file name extension, it is not additinally appended.
     * 
     * @param archive the name of the archive
     * @param fileExtension the file extension (e.g. ".tar")
     * @param destination the parent path
     * @return the newly created file
     * @throws IOException
     */
    protected File createNewArchiveFile(String archive, String fileExtension, File destination) throws IOException {
        if (!archive.endsWith(fileExtension)) {
            archive += fileExtension;
        }

        File file = new File(destination, archive);
        file.createNewFile();

        return file;
    }

    /**
     * Recursion entry point for {@link #writeToArchive(File, File[], ArchiveOutputStream)}.
     * <p>
     * Recursively writes all given source {@link File}s into the given {@link ArchiveOutputStream}.
     * 
     * @param sources the files to write in to the archive
     * @param archive the archive to write into
     * @throws IOException
     */
    protected void writeToArchive(File[] sources, ArchiveOutputStream archive) throws IOException {
        for (File source : sources) {
            if (source.isFile()) {
                writeToArchive(source.getParentFile(), new File[] { source }, archive);
            } else {
                writeToArchive(source, source.listFiles(), archive);
            }
        }
    }

    /**
     * Recursively writes all given source {@link File}s into the given {@link ArchiveOutputStream}. The paths of the
     * sources in the archive will be relative to the given parent {@code File}.
     * 
     * @param parent the parent file node for computing a relative path (see {@link #relativePath(File, File)})
     * @param sources the files to write in to the archive
     * @param archive the archive to write into
     * @throws IOException
     */
    protected void writeToArchive(File parent, File[] sources, ArchiveOutputStream archive) throws IOException {
        for (File source : sources) {
            String relativePath = IOUtils.relativePath(parent, source);

            createArchiveEntry(source, relativePath, archive);

            if (source.isDirectory()) {
                writeToArchive(parent, source.listFiles(), archive);
            }
        }
    }

    /**
     * Creates a new {@link ArchiveEntry} in the given {@link ArchiveOutputStream}, and copies the given {@link File}
     * into the new entry.
     * 
     * @param file the file to add to the archive
     * @param entryName the name of the archive entry
     * @param archive the archive to write to
     * @throws IOException
     */
    protected void createArchiveEntry(File file, String entryName, ArchiveOutputStream archive) throws IOException {
        ArchiveEntry entry = archive.createArchiveEntry(file, entryName);
        archive.putArchiveEntry(entry);

        if (!entry.isDirectory()) {
            try (FileInputStream input = new FileInputStream(file)) {
                IOUtils.copy(input, archive);
            }
        }

        archive.closeArchiveEntry();
    }

}
