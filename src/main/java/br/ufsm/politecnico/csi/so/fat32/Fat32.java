package br.ufsm.politecnico.csi.so.fat32;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class Fat32 implements FileSystem {

    private Disco disco = new Disco();
    private int[] fat = new int[Disco.NumeroBlocos];
    private static final int BlocoFat = 0;
    private static final int BlocoDiretorio = 1;
    private static final int BlocoOcupado = -1;

    @Override
    public void create(String fileName, byte[] data) {
        this.disco = disco;
        try {
            if(!disco.init()){
                inicializaFat();
                gravaDiretorio();

            }
            else{
                leFat();
                leDiretorio();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void leFat(){
        try {
            byte[] bloco = disco.read(BlocoFat);
            ByteBuffer buffer = ByteBuffer.wrap(bloco);

            for(int i = 0; i < fat.length; i++){

                int n = buffer.getInt();
                fat[i] = n;

            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void leDiretorio(){

    }
    private void gravaDiretorio(){



    }

    private void inicializaFat(){

        fat[BlocoFat] = BlocoOcupado;
        fat[BlocoDiretorio] = BlocoOcupado;
        gravaFat();

    };

    private void gravaFat(){
        byte[] bloco =new byte[Disco.TamanhoBloco];

        ByteBuffer buffer = ByteBuffer.allocate(Disco.TamanhoBloco);
        for(int i : fat){
            buffer.putInt(i);
        }
        try {
            disco.write(BlocoFat, buffer.array());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void append(String fileName, byte[] data) {

    }

    @Override
    public byte[] read(String fileName, int offset, int limit) {
        return new byte[0];
    }

    @Override
    public void remove(String fileName) {

    }

    @Override
    public int freeSpace() {
        return 0;
    }

    private List<EntradaDiretorio> diretorio = new ArrayList<>();

    private class EntradaDiretorio{

        private String fileName;
        private int TamanhoArquivo;
        private int blocoInicial;

    }

}
