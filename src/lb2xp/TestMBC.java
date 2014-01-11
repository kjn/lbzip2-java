package lb2xp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.security.MessageDigest;

import org.junit.Test;

public class TestMBC
{
    private static String md5( byte[] arr )
        throws Exception
    {
        MessageDigest md = MessageDigest.getInstance( "MD5" );
        md.update( arr );
        StringBuilder sb = new StringBuilder();
        for ( byte c : md.digest() )
            sb.append( String.format( "%02x", c ) );
        return sb.toString();
    }

    private void oneFile( String name, String md5 )
        throws Exception
    {
        try
        {
            File bzf = new File( "test-data/" + name + ".bz2" );
            System.setIn( new FileInputStream( bzf ) );
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            System.setOut( new PrintStream( out ) );
            MBC mbc = new MBC();
            mbc.expand();
            if ( md5 == null )
                fail();
            assertEquals( md5, md5( out.toByteArray() ) );
        }
        catch ( RuntimeException e )
        {
            if ( md5 != null )
                throw e;
        }
    }

    @Test
    public void testExpand()
        throws Exception
    {
        oneFile( "32767", "2debfdcf79f03e4a65a667d21ef9de14" );
        oneFile( "ch255", "3d58e437e3685a77276da1199aace837" );
        oneFile( "codelen20", "7fc56270e7a70fa81a5935b72eacbe29" );
        oneFile( "concat", "2debfdcf79f03e4a65a667d21ef9de14" );
        oneFile( "crc1", null );
        oneFile( "crc2", null );
        oneFile( "cve2", null );
        oneFile( "cve", null );
        oneFile( "empty", "d41d8cd98f00b204e9800998ecf8427e" );
        oneFile( "fib", "4d702db5a6f546feb9f739aa946b4f56" );
        oneFile( "gap", "2debfdcf79f03e4a65a667d21ef9de14" );
        oneFile( "incomp-1", "8bab041a19e4cf0c244da418b15676f1" );
        oneFile( "incomp-2", "8bab041a19e4cf0c244da418b15676f1" );
        oneFile( "load0", null );
        oneFile( "overrun", null );
        oneFile( "rand", "2debfdcf79f03e4a65a667d21ef9de14" );
        oneFile( "repet", "bf3335ea7e712d847d189b087e45c54b" );
        oneFile( "trash", "2debfdcf79f03e4a65a667d21ef9de14" );
        oneFile( "void", null );
    }
}
