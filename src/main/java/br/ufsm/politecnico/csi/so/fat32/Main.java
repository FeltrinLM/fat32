package br.ufsm.politecnico.csi.so.fat32;

public class Main {
    public static void main(String[] args) {

        FileSystem fs = new Fat32();

        String nomeArquivo = "teste.txt";
        byte[] dados = "Olá, sistema de arquivos FAT32!".getBytes();

        fs.create(nomeArquivo, dados);

        byte[] lido = fs.read(nomeArquivo, 0, -1);

        System.out.println("Conteúdo lido:");
        System.out.println(new String(lido));

    }
}