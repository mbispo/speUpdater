package speudater;

import br.jus.tjms.pontoeletronico.to.UpdateInfoTO;
import br.jus.tjms.pontoeletronico.to.UpdateScriptAcaoTO;

import com.thoughtworks.xstream.XStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
//import br.jus.tjms.pontoeletronico.client.Constantes;


/**
 *
 * @author marcos.bispo
 */
public class SpeUdater {

    private static final String ROOT = "update/";
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        /*
        delay 10 segundos;
        descompacta update.zip;
        executa updatescript.xml**: formato XML, seções executar, excluir, etc
        -executa exclusões;
        -executa executáveis (instalação de drivers, etc);
        -executa sql
        -etc
        chama aplicação principal novamente
        */
        
        try {
            Thread.sleep(10000l);
            
            String appPrincipal = null;
            String url = null;
            
            try {
                appPrincipal = args[0];
                url = args[1];
            } catch (ArrayIndexOutOfBoundsException are) {
                throw new Exception("requer 2 parametros: appprincipal url");
            }
            
            if (url!=null && url.equalsIgnoreCase("RESTART")) {
                chamarAppPrincipal(appPrincipal);
            } else if (url!=null) {
                download(url);
                descompactar();
                copiarArquivos(new File(ROOT), new File("").getAbsolutePath());
                limpar();
                executarScript();
                chamarAppPrincipal(appPrincipal);
            }
        } catch (Exception ex) {
            Logger.getLogger(SpeUdater.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static void descompactar() throws IOException {
        int BUFFER = 2048;
        BufferedOutputStream dest = null;
        BufferedInputStream is = null;
        ZipEntry entry;
        ZipFile zipfile = new ZipFile("update.zip");
        Enumeration e = zipfile.entries();
        (new File(ROOT)).mkdir();
        while (e.hasMoreElements()) {
            entry = (ZipEntry) e.nextElement();
            
            System.out.println("Extraindo: " + entry);
            
            if (entry.isDirectory()) {
                (new File(ROOT + entry.getName())).mkdir();
            } else {
                (new File(ROOT + entry.getName())).createNewFile();
                is = new BufferedInputStream(zipfile.getInputStream(entry));
                int count;
                byte data[] = new byte[BUFFER];
                FileOutputStream fos = new FileOutputStream(ROOT + entry.getName());
                dest = new BufferedOutputStream(fos, BUFFER);
                while ((count = is.read(data, 0, BUFFER))
                        != -1) {
                    dest.write(data, 0, count);
                }
                dest.flush();
                dest.close();
                is.close();
            }
        }
    }

    private static void executarScript() throws Exception {
        // ler updatescript previamente gravado e executar as ações
        
        UpdateInfoTO updateInfo = obterUpdateInfo();
        
        List<UpdateScriptAcaoTO> acoes = updateInfo.getUpdateScript().getAcoes();
        
        // processar        
        for (UpdateScriptAcaoTO acao: acoes) {
            
            switch (acao.getTipoAcao()) {
                case CRIAR_CHAVE_REGISTRO: {
                    criarChaveRegistro(acao.getChave(), acao.getValor());
                    break;
                }
                case ALTERAR_CHAVE_REGISTRO: {
                    alterarChaveRegistro(acao.getChave(), acao.getValor());
                    break;
                }
                case EXCLUIR_CHAVE_REGISTRO: {
                    excluirChaveRegistro(acao.getChave(), acao.getValor());
                    break;
                }
                case EXCLUIR_ARQUIVO: {
                    excluirArquivo(acao.getChave(), acao.getValor());
                    break;
                }
                case EXECUTAR_ARQUIVO: {
                    executarArquivo(acao.getChave(), acao.getValor());
                    break;
                }
                case EXECUTAR_SQL: {                    
                    break;
                }
                
            }
            
        }

    }

    private static void chamarAppPrincipal(String app) {
        
        try {
            
            String workingDir = Paths.get(".").toAbsolutePath().normalize().toString()+File.separator;
            System.out.println("workingDir = "+workingDir);
            
            if (!workingDir.contains("spe")) {
                workingDir = "c:\\sistemas\\spe-javafx\\";
                System.out.println("workingDir = "+workingDir);
            }
            
            ProcessBuilder pb = new ProcessBuilder(workingDir+app);
            
            pb.directory(new File(workingDir));
            File log = new File("updater.log");

            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
            Process p = pb.start();
            
            assert pb.redirectInput() == ProcessBuilder.Redirect.INHERIT;
            assert pb.redirectOutput().file() == log;
            assert p.getInputStream().read() == -1;

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        System.exit(0);
    }
    
    private static void copiarArquivos(File f, String dir) throws IOException {
        File[] files = f.listFiles();
        for (File ff : files) {
            if (ff.isDirectory()) {
                new File(dir + "/" + ff.getName()).mkdir();
                copiarArquivos(ff, dir + "/" + ff.getName());
            } else {
                copy(ff.getAbsolutePath(), dir + "/" + ff.getName());
            }
        }
    }

    public static void copy(String srFile, String dtFile) throws FileNotFoundException, IOException {
        File f1 = new File(srFile);
        File f2 = new File(dtFile);

        InputStream in = new FileInputStream(f1);

        OutputStream out = new FileOutputStream(f2);

        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }
    
    private static void limpar() {
        System.out.println("\nLimpando...");
        File f = new File("update.zip");
        f.delete();
        remove(new File(ROOT));
        new File(ROOT).delete();
    }

    private static void remove(File f) {
        File[] files = f.listFiles();
        for (File ff : files) {
            if (ff.isDirectory()) {
                remove(ff);
                ff.delete();
            } else {
                ff.delete();
            }
        }
    }
    
    private static void download(String link) throws MalformedURLException, IOException {
        URL url = new URL(link);

        URLConnection conn = url.openConnection();
        InputStream is = conn.getInputStream();
        long max = conn.getContentLength();
        
        System.out.println("Baixando update...\nTamanho (compactado): " + max + " Bytes");
        
        BufferedOutputStream fOut = new BufferedOutputStream(new FileOutputStream(new File("update.zip")));
        byte[] buffer = new byte[32 * 1024];
        int bytesRead = 0;
        int in = 0;
        while ((bytesRead = is.read(buffer)) != -1) {
            in += bytesRead;
            fOut.write(buffer, 0, bytesRead);
        }
        fOut.flush();
        fOut.close();
        is.close();
        System.out.println("\nDownload completo!");
    }

    private static UpdateInfoTO obterUpdateInfo() throws Exception {
        UpdateInfoTO updateInfo = null;
        
        String nome = "updateInfo.xml";
        File file = new File(nome);
        
        if (!file.exists()) {
            //throw new Exception("Arquivo "+nome+" não existe!");
        } else {
        
            try{
                XStream xstream = new XStream();
                updateInfo = (UpdateInfoTO) xstream.fromXML(file);       
            }catch(Exception e){
                e.printStackTrace();
                throw new Exception("Falha ao ler "+nome+": "+e.getMessage());
            }
            
        }
        
        return updateInfo;        
        
    }

    private static void criarChaveRegistro(String chave, String valor) {
        try {
            // TODO criar chave de registro
        } catch (Exception e) {
            Logger.getLogger(SpeUdater.class.getName()).log(Level.SEVERE, "Falha ao criar chave de registro ("+chave+" = "+valor+")!", e);
        }
    }

    private static void alterarChaveRegistro(String chave, String valor) {
        try {
            // TODO alterar chave de registro
        } catch (Exception e) {
            Logger.getLogger(SpeUdater.class.getName()).log(Level.SEVERE, "Falha ao alterar chave de registro ("+chave+" = "+valor+")!", e);
        }
    }

    private static void excluirChaveRegistro(String chave, String valor) {
        try {
            // TODO excluir chave de registro
        } catch (Exception e) {
            Logger.getLogger(SpeUdater.class.getName()).log(Level.SEVERE, "Falha ao criar excluir chave de registro "+chave+"!", e);
        }

    }

    private static void excluirArquivo(String chave, String valor) {
        try {
            // excluir arquivo
            File file = new File(valor);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            Logger.getLogger(SpeUdater.class.getName()).log(Level.SEVERE, "Falha ao excluir arquivo "+valor+"!", e);
        }
        
    }

    private static void executarArquivo(String chave, String valor) {

        try {
            
            String workingDir = Paths.get(".").toAbsolutePath().normalize().toString()+File.separator;
            System.out.println("workingDir = "+workingDir);
            
            if (!workingDir.contains("spe")) {
                workingDir = "c:\\sistemas\\spe-javafx\\";
                System.out.println("workingDir = "+workingDir);
            }
            
            ProcessBuilder pb = new ProcessBuilder(workingDir+valor);
            
            pb.directory(new File(workingDir));
            File log = new File(chave+".log");

            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
            Process p = pb.start();
            
            assert pb.redirectInput() == ProcessBuilder.Redirect.INHERIT;
            assert pb.redirectOutput().file() == log;
            assert p.getInputStream().read() == -1;

        } catch (Exception ex) {
            ex.printStackTrace();
            Logger.getLogger(SpeUdater.class.getName()).log(Level.SEVERE, "Falha ao executar arquivo "+valor+"!", ex);
        }        
    }
    
}