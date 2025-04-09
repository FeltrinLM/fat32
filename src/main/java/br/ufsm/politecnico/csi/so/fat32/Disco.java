package br.ufsm.politecnico.csi.so.fat32;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class Disco {

    public static final int TamanhoBloco = 64 *1024;
    public static final int NumeroBlocos = 1024;
    private RandomAccessFile raf;

    public Disco(){}

    public boolean init() throws IOException {
        File f = new File ("disco.dat");
        boolean exists = f.exists();

        if(!exists){
            raf = new RandomAccessFile(f,"rws");
            raf.setLength(NumeroBlocos*TamanhoBloco);
        }
        return exists;
    }

    public byte [] read(int numBlocos) throws IOException {
        if ( numBlocos < 0 || numBlocos > NumeroBlocos ) {
            throw new IndexOutOfBoundsException("Numero blocos invalido");
        }
        raf.seek(numBlocos * TamanhoBloco);
        byte [] read = new byte[TamanhoBloco];
        raf.read(read);
        return read;
    }

    public void write(int numBlocos, byte [] data) throws IOException {

        if(numBlocos < 0 || numBlocos > NumeroBlocos ) {
            throw new IllegalArgumentException("Num de blocos deve ser entre 0 e "+(NumeroBlocos - 1));
        }

        if(data == null || data.length > TamanhoBloco){
            throw new IllegalArgumentException("Num de blocos deve ser entre 0 e "+(NumeroBlocos - 1));
        }
    }



}
