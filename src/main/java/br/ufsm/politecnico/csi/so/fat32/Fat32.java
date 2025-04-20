package br.ufsm.politecnico.csi.so.fat32;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Fat32 implements FileSystem {

    private Disco disco = new Disco();
    private int[] fat = new int[Disco.NumeroBlocos];
    private static final int BlocoFat = 0;
    private static final int BlocoDiretorio = 1;
    private static final int BlocoOcupado = -1;

    // Lista de entradas de diretório
    private List<EntradaDiretorio> diretorio = new ArrayList<>();

    @Override
    public void create(String fileName, byte[] data) {
        try {
            // inicializa ou carrega FAT
            if (!disco.init()) {
                inicializaFat();
            } else {
                leFat();
            }

            // carrega diretório
            leDiretorio();

            // calcula blocos necessários
            int blocosNecessarios = (int) Math.ceil((double) data.length / Disco.TamanhoBloco);
            int[] blocos = new int[blocosNecessarios];
            int encontrados = 0;
            for (int i = 2; i < fat.length && encontrados < blocosNecessarios; i++) {
                if (fat[i] == 0) {
                    blocos[encontrados++] = i;
                }
            }
            if (encontrados < blocosNecessarios) {
                throw new RuntimeException("Espaço insuficiente");
            }

            // grava dados e atualiza FAT
            for (int i = 0; i < blocos.length; i++) {
                int blocoAtual = blocos[i];
                int proximo = (i == blocos.length - 1) ? -1 : blocos[i + 1];
                fat[blocoAtual] = proximo;

                int start = i * Disco.TamanhoBloco;
                int end = Math.min(start + Disco.TamanhoBloco, data.length);
                byte[] pedaco = new byte[Disco.TamanhoBloco];
                System.arraycopy(data, start, pedaco, 0, end - start);

                disco.write(blocoAtual, pedaco);
            }

            // adiciona ao diretório em memória
            EntradaDiretorio e = new EntradaDiretorio();
            e.fileName = fileName;
            e.TamanhoArquivo = data.length;
            e.blocoInicial = blocos[0];
            diretorio.add(e);

            // salva FAT e diretório no disco
            gravaFat();
            gravaDiretorio();

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void leFat() {
        try {
            byte[] bloco = disco.read(BlocoFat);
            ByteBuffer buffer = ByteBuffer.wrap(bloco);
            for (int i = 0; i < fat.length; i++) {
                fat[i] = buffer.getInt();
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void gravaFat() {
        ByteBuffer buffer = ByteBuffer.allocate(Disco.TamanhoBloco);
        for (int entry : fat) {
            buffer.putInt(entry);
        }
        try {
            disco.write(BlocoFat, buffer.array());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void leDiretorio() {
        try {
            byte[] bloco = disco.read(BlocoDiretorio);
            ByteBuffer buffer = ByteBuffer.wrap(bloco);

            diretorio.clear();
            while (buffer.remaining() >= 32 + 4 + 4) {
                byte[] nomeBytes = new byte[32];
                buffer.get(nomeBytes);
                String nome = new String(nomeBytes).trim();
                int tamanho = buffer.getInt();
                int inicio = buffer.getInt();
                if (!nome.isEmpty()) {
                    EntradaDiretorio e = new EntradaDiretorio();
                    e.fileName = nome;
                    e.TamanhoArquivo = tamanho;
                    e.blocoInicial = inicio;
                    diretorio.add(e);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void gravaDiretorio() {
        ByteBuffer buffer = ByteBuffer.allocate(Disco.TamanhoBloco);
        for (EntradaDiretorio e : diretorio) {
            byte[] nome = new byte[32];
            byte[] nomeBytes = e.fileName.getBytes();
            System.arraycopy(nomeBytes, 0, nome, 0, Math.min(nomeBytes.length, 32));
            buffer.put(nome);
            buffer.putInt(e.TamanhoArquivo);
            buffer.putInt(e.blocoInicial);
        }
        try {
            disco.write(BlocoDiretorio, buffer.array());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void inicializaFat() {
        fat[BlocoFat] = BlocoOcupado;
        fat[BlocoDiretorio] = BlocoOcupado;
        gravaFat();
    }

    @Override
    public byte[] read(String fileName, int offset, int limit) {
        try {
            // busca entrada no diretório
            EntradaDiretorio entrada = null;
            for (EntradaDiretorio e : diretorio) {
                if (e.fileName.equals(fileName)) {
                    entrada = e;
                    break;
                }
            }
            if (entrada == null) {
                throw new RuntimeException("Arquivo não encontrado: " + fileName);
            }
            // lê todo o conteúdo encadeado
            byte[] arquivoCompleto = new byte[entrada.TamanhoArquivo];
            int blocoAtual = entrada.blocoInicial;
            int pos = 0;
            while (blocoAtual > 0 && pos < arquivoCompleto.length) {
                byte[] bloco = disco.read(blocoAtual);
                int toCopy = Math.min(Disco.TamanhoBloco, arquivoCompleto.length - pos);
                System.arraycopy(bloco, 0, arquivoCompleto, pos, toCopy);
                pos += toCopy;
                blocoAtual = fat[blocoAtual];
            }
            // ajusta offset e limit
            int fim = (limit < 0)
                    ? entrada.TamanhoArquivo
                    : Math.min(entrada.TamanhoArquivo, offset + limit);
            if (offset > entrada.TamanhoArquivo) {
                return new byte[0];
            }
            byte[] resultado = new byte[fim - offset];
            System.arraycopy(arquivoCompleto, offset, resultado, 0, fim - offset);
            return resultado;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void append(String fileName, byte[] data) {
        // implementação futura
    }

    @Override
    public void remove(String fileName) {
        // implementação futura
    }

    @Override
    public int freeSpace() {
        int livres = 0;
        for (int entry : fat) {
            if (entry == 0) livres++;
        }
        return livres * Disco.TamanhoBloco;
    }

    // classe interna de diretório
    private static class EntradaDiretorio {
        String fileName;
        int TamanhoArquivo;
        int blocoInicial;
    }
}
