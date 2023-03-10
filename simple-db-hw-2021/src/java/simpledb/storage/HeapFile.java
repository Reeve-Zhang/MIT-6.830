package simpledb.storage;

import edu.princeton.cs.algs4.Heap;
import simpledb.common.*;
import simpledb.transaction.TransactionAbortedException;
import simpledb.transaction.TransactionId;

import javax.xml.crypto.Data;
import java.io.*;
import java.nio.Buffer;
import java.util.*;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 * 
 * @see HeapPage#HeapPage
 * @author Sam Madden
 */
public class HeapFile implements DbFile {

    private File file;
    private RandomAccessFile rf;
    private TupleDesc td;
    private Iterator<Tuple> it;

    /**
     * Constructs a heap file backed by the specified file.
     * 
     * @param f
     *            the file that stores the on-disk backing store for this heap
     *            file.
     */
    public HeapFile(File f, TupleDesc td) {
        this.file = f;
        this.td = td;
        try{
            this.rf = new RandomAccessFile(f, "rw");
        } catch (FileNotFoundException e){
            e.printStackTrace();
        }
        //TODO: iterator?
    }

    /**
     * Returns the File backing this HeapFile on disk.
     * 
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        return this.file;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere to ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     * 
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        // hashing the absolute file name of file (including path) underlying the heapFile
        return file.getAbsoluteFile().hashCode();
        //throw new UnsupportedOperationException("implement this");
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     * 
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        return this.td;
    }

    // see DbFile.java for javadocs
    public Page readPage(PageId pid) {
        if (pid.getTableId() != this.getId()) {
            throw new NoSuchElementException();
        }
        FileInputStream fis = null;
        try
        {
            fis = new FileInputStream(this.file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        int pageSize = BufferPool.getPageSize();
        int pgNo = pid.getPageNumber();
        int offSet = pageSize * pgNo;
        long fileLength = this.file.length();
        int size = Math.min((int) fileLength - offSet, pageSize);
        byte[] pageData = new byte[size];
        try
        {
            rf.seek(offSet);
            rf.readFully(pageData);
            return new HeapPage((HeapPageId) pid, pageData);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (IndexOutOfBoundsException e) {
            System.out.println("fileLength: " + fileLength);
            System.out.println("PageSize: " + pageSize);
            System.out.println("PgNo: " + pgNo);
            System.out.println("offSet: " + offSet);
            System.out.println("size: " + offSet);
        }
        return null;
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        int offset = BufferPool.getPageSize() * page.getId().getPageNumber();
        rf.seek(offset);
        rf.write(page.getPageData());
        page.markDirty(false, null);
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        return (int) Math.ceil(file.length() * 1.0 / BufferPool.getPageSize());
    }

    // see DbFile.java for javadocs
    public List<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        if (!t.getTupleDesc().equals(td)) {
            throw new DbException("Mismatch tupleDesc");
        }
        HeapPage page = null;
        for (int i = 0; i < numPages(); i += 1) {
            HeapPageId pid = new HeapPageId(this.getId(), i);
            page = (HeapPage) Database.getBufferPool().getPage(tid, pid, Permissions.READ_WRITE);
            if (page.getNumEmptySlots() > 0) {
                page.insertTuple(t);
                List<Page> pages = new ArrayList<>();
                pages.add(page);
                return pages;
            }
        }
        // if all the pages have no empty slots
        byte[] pageData = HeapPage.createEmptyPageData();
        HeapPageId newPid = new HeapPageId(this.getId(), numPages());
        HeapPage appendPage = new HeapPage(newPid, pageData);

        writePage(appendPage); // write page before inserting the tuple
        appendPage.insertTuple(t);

        //writePage(appendPage); // write page
        List<Page> pages = new ArrayList<>();
        pages.add(appendPage);
        return pages;
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        // some code goes here
        if (!t.getTupleDesc().equals(td)) {
            throw new DbException("Mismatched tupleDesc");
        }
        if (this.getId() != t.getRecordId().getPageId().getTableId()) {
            throw new DbException("Mismatched tableId");
        }
        HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid, t.getRecordId().getPageId(), Permissions.READ_WRITE);
        try {
            page.deleteTuple(t);
        } catch (DbException e) {
            e.printStackTrace();
        }
        ArrayList<Page> pages = new ArrayList<>();
        pages.add(page);
        return pages;
    }

    // see DbFile.java for javadocs
    /**
     * Returns an iterator over all the tuples stored in this DbFile. The
     * iterator must use {@link BufferPool#getPage}, rather than
     * {@link #readPage} to iterate through the pages.
     *
     * @return an iterator over all the tuples stored in this DbFile.
     */
    public DbFileIterator iterator(TransactionId tid) {
        return new HeapFileIterator(this, tid);
    }

}

