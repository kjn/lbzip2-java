package lb2xp;

import static org.junit.Assert.assertEquals;

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
        File bzf = new File( "test-data/" + name + ".bz2" );
        System.setIn( new FileInputStream( bzf ) );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut( new PrintStream( out ) );
        MBC mbc = new MBC();
        mbc.expand();
        assertEquals( md5, md5( out.toByteArray() ) );
    }

    @Test
    public void testExpand()
        throws Exception
    {
        oneFile( "concat", "2debfdcf79f03e4a65a667d21ef9de14" );
    }
}
