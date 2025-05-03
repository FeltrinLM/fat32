package br.ufsm.politecnico.csi.so.fat32;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Fat32 implements FileSystem {

    private Disco disco = new Disco();
    private int[] fat = new int[Disco.NumeroBlocos];
    private static final int BlocoFat = 1;
    private static final int BlocoDiretorio = 0;
    private static final int BlocoOcupado = -1;

    private List<EntradaDiretorio> diretorio = new ArrayList<>();

    public Fat32() {
        try {
            // ao criar o objeto, já carrega disco, FAT e diretório
            if (!disco.init()) {
                inicializaFat();
            } else {
                leFat();
            }
            leDiretorio();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void create(String fileName, byte[] data) {
        try {
            // 0) Inicializa disco/FAT e carrega FAT em memória
            if (!disco.init()) {
                inicializaFat();
            } else {
                leFat();
            }

            // 1) Carrega diretório atual
            leDiretorio();

            // 2) Valida nome no formato 8.3
            if (!fileName.matches("^[^\\.]{1,8}(\\.[^\\.]{1,3})?$")) {
                throw new IllegalArgumentException(
                        "Nome inválido. Até 8 caracteres e extensão opcional de até 3."
                );
            }

            // 3) Verifica duplicata
            for (EntradaDiretorio e : diretorio) {
                if (e.fileName.equalsIgnoreCase(fileName)) {
                    throw new RuntimeException("Arquivo já existe: " + fileName);
                }
            }

            // 4) Calcula quantos blocos são necessários
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

            // 5) Grava cada bloco e atualiza a FAT
            for (int i = 0; i < blocosNecessarios; i++) {
                int atual = blocos[i];
                // se for o último, sinaliza fim com BlocoOcupado (-1)
                int proximo = (i == blocosNecessarios - 1) ? BlocoOcupado : blocos[i + 1];
                fat[atual] = proximo;

                byte[] blocoData = new byte[Disco.TamanhoBloco];
                int offset = i * Disco.TamanhoBloco;
                int len = Math.min(Disco.TamanhoBloco, data.length - offset);
                System.arraycopy(data, offset, blocoData, 0, len);
                disco.write(atual, blocoData);
            }

            // 6) Adiciona entrada no diretório em memória
            EntradaDiretorio nova = new EntradaDiretorio();
            nova.fileName = fileName;
            nova.TamanhoArquivo = data.length;
            nova.blocoInicial = blocos[0];
            diretorio.add(nova);

            // 7) Persiste FAT e diretório no disco
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
            while (buffer.remaining() >= 11 + 4 + 4) {
                byte[] nomeBytes = new byte[11];
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
            byte[] nome = new byte[11];
            byte[] nomeBytes = e.fileName.getBytes();
            System.arraycopy(nomeBytes, 0, nome, 0, Math.min(nomeBytes.length, 11));
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
            // 1) Busca a entrada do diretório
            EntradaDiretorio entrada = null;
            for (EntradaDiretorio e : diretorio) {
                if (e.fileName.equals(fileName)) {
                    entrada = e;
                    break;
                }
            }
            // 2) Arquivo não existe?
            if (entrada == null) {
                throw new RuntimeException("Arquivo não encontrado: " + fileName);
            }

            // 3) Valida offset e limit
            if (offset < 0 || offset > entrada.TamanhoArquivo) {
                throw new IllegalArgumentException("Offset fora dos limites do arquivo.");
            }
            if (limit > 0 && offset + limit > entrada.TamanhoArquivo) {
                throw new IllegalArgumentException("Leitura além do fim do arquivo.");
            }

            // 4) Lê todo o conteúdo encadeado via FAT
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

            // 5) Calcula até onde vai (limit < 0 significa até o fim)
            int fim = (limit < 0)
                    ? entrada.TamanhoArquivo
                    : Math.min(entrada.TamanhoArquivo, offset + limit);

            // 6) Retorna o pedaço solicitado
            byte[] resultado = new byte[fim - offset];
            System.arraycopy(arquivoCompleto, offset, resultado, 0, fim - offset);
            return resultado;

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }


    @Override
    public void append(String fileName, byte[] data) {
        try {
            // recarrega FAT e diretório
            leFat();
            leDiretorio();

            // busca entrada
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

            int oldSize = entrada.TamanhoArquivo;
            int offsetInBlock = oldSize % Disco.TamanhoBloco;
            int remaining = data.length;
            int dataPos = 0;

            // encontra último bloco
            int last = entrada.blocoInicial;
            while (fat[last] > 0) {
                last = fat[last];
            }

            // 1) preenche o final do bloco existente, se couber
            if (offsetInBlock != 0) {
                byte[] blocoConteudo = disco.read(last);
                int canWrite = Math.min(Disco.TamanhoBloco - offsetInBlock, remaining);
                System.arraycopy(data, dataPos, blocoConteudo, offsetInBlock, canWrite);
                disco.write(last, blocoConteudo);
                remaining -= canWrite;
                dataPos += canWrite;
            }

            // 2) aloca blocos adicionais para o que restou
            int prev = last;
            while (remaining > 0) {
                // encontra próximo bloco livre
                int novo = -1;
                for (int i = 2; i < fat.length; i++) {
                    if (fat[i] == 0) {
                        novo = i;
                        break;
                    }
                }
                if (novo == -1) {
                    throw new RuntimeException("Espaço insuficiente para append");
                }

                // encadeia na FAT
                fat[prev] = novo;
                prev = novo;

                // grava pedaço no bloco
                byte[] pedaco = new byte[Disco.TamanhoBloco];
                int canWrite = Math.min(Disco.TamanhoBloco, remaining);
                System.arraycopy(data, dataPos, pedaco, 0, canWrite);
                disco.write(novo, pedaco);

                dataPos += canWrite;
                remaining -= canWrite;
            }

            // sinaliza fim de arquivo
            fat[prev] = -1;

            // atualiza tamanho e persiste
            entrada.TamanhoArquivo = oldSize + data.length;
            gravaFat();
            gravaDiretorio();

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void remove(String fileName) {
        try {
            // 0) Recarrega FAT e diretório
            leFat();
            leDiretorio();

            // 1) Localiza a entrada a remover
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

            // 2) Percorre a cadeia de blocos e limpa o conteúdo antes de liberar
            int bloco = entrada.blocoInicial;
            while (bloco > 0) {
                int proximo = fat[bloco];

                // Limpa fisicamente o bloco (grava zeros)
                byte[] vazio = new byte[Disco.TamanhoBloco];
                disco.write(bloco, vazio);

                // Marca bloco como livre na FAT
                fat[bloco] = 0;

                bloco = proximo;
            }

            // 3) Remove a entrada do diretório em memória
            diretorio.remove(entrada);

            // 4) Persiste FAT e diretório zerados
            gravaFat();
            gravaDiretorio();

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }



    @Override
    public int freeSpace() {
        int livres = 0;
        for (int entry : fat) {
            if (entry == 0) livres++;
        }
        return livres * Disco.TamanhoBloco;
    }

    private static class EntradaDiretorio {
        String fileName;
        int TamanhoArquivo;
        int blocoInicial;
    }
}
