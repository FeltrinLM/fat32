package br.ufsm.politecnico.csi.so.fat32;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {

        //new java.io.File("disco.dat").delete();
        FileSystem fs = new Fat32();
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.println("=== Menu Sistema de Arquivos FAT32 ===");
            System.out.println("1) Criar arquivo");
            System.out.println("2) Ler arquivo existente");
            System.out.println("3) Adicionar conteúdo a arquivo (append)");
            System.out.println("4) Remover arquivo");
            System.out.println("5) Ver espaço livre");
            System.out.println("6) Sair");
            System.out.print("Escolha uma opção: ");
            String opcao = sc.nextLine().trim();

            switch (opcao) {
                case "1":
                    System.out.print("Nome do arquivo (formato 8.3): ");
                    String nomeCriar = sc.nextLine().trim();
                    System.out.print("Conteúdo do arquivo: ");
                    String conteudoCriar = sc.nextLine();
                    try {
                        fs.create(nomeCriar, conteudoCriar.getBytes());
                        System.out.println("✓ Arquivo '" + nomeCriar + "' criado com sucesso!\n");
                    } catch (Exception e) {
                        System.out.println("✗ Erro ao criar: " + e.getMessage() + "\n");
                    }
                    break;

                case "2":
                    System.out.print("Nome do arquivo para ler: ");
                    String nomeLer = sc.nextLine().trim();
                    try {
                        byte[] dados = fs.read(nomeLer, 0, -1);
                        System.out.println("Conteúdo de '" + nomeLer + "':");
                        System.out.println(new String(dados) + "\n");
                    } catch (Exception e) {
                        System.out.println("✗ Erro ao ler: " + e.getMessage() + "\n");
                    }
                    break;

                case "3":
                    System.out.print("Nome do arquivo para adicionar conteúdo: ");
                    String nomeAppend = sc.nextLine().trim();
                    System.out.print("Texto a ser adicionado: ");
                    String textoAppend = sc.nextLine();
                    try {
                        fs.append(nomeAppend, textoAppend.getBytes());
                        System.out.println("✓ Conteúdo adicionado ao arquivo '" + nomeAppend + "'\n");
                    } catch (Exception e) {
                        System.out.println("✗ Erro no append: " + e.getMessage() + "\n");
                    }
                    break;

                case "4":
                    System.out.print("Nome do arquivo a remover: ");
                    String nomeRemover = sc.nextLine().trim();
                    try {
                        fs.remove(nomeRemover);
                        System.out.println("✓ Arquivo '" + nomeRemover + "' removido com sucesso!\n");
                    } catch (Exception e) {
                        System.out.println("✗ Erro ao remover: " + e.getMessage() + "\n");
                    }
                    break;

                case "5":
                    int espaco = fs.freeSpace();
                    System.out.println("Espaço livre no disco: " + espaco + " bytes\n");
                    break;

                case "6":
                    System.out.println("Encerrando...");
                    sc.close();
                    return;

                default:
                    System.out.println("Opção inválida! Tente novamente.\n");
            }
        }
    }
}
