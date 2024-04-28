package arquivos;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;

import aeds3.Arquivo;
import aeds3.ArvoreBMais;
import aeds3.HashExtensivel;
import aeds3.ListaInvertida;
import aeds3.ParIntInt;
import entidades.Livro;

public class ArquivoLivros extends Arquivo<Livro> {

  HashExtensivel<ParIsbnId> indiceIndiretoISBN;
  ArvoreBMais<ParIntInt> relLivrosDaCategoria;
  ListaInvertida indiceInvertido;

  public ArquivoLivros() throws Exception {
    super("livros", Livro.class.getConstructor());
    indiceIndiretoISBN = new HashExtensivel<>(
        ParIsbnId.class.getConstructor(),
        4,
        "dados/livros_isbn.hash_d.db",
        "dados/livros_isbn.hash_c.db");
    relLivrosDaCategoria = new ArvoreBMais<>(ParIntInt.class.getConstructor(), 4, "dados/livros_categorias.btree.db");
    indiceInvertido = new ListaInvertida(4, "dados/dicionario.listainv.db", "dados/blocos.listainv.db"); // inicia o arquivo da lista invertida

  }

  @Override
  public int create(Livro obj) throws Exception {
    int id = super.create(obj);
    obj.setID(id);
    indiceIndiretoISBN.create(new ParIsbnId(obj.getIsbn(), obj.getID()));
    relLivrosDaCategoria.create(new ParIntInt(obj.getIdCategoria(), obj.getID()));
    
    String [] palavrasChaveSW = obj.getTitulo().toLowerCase().split(" "); // divide o titulo em palavras
    String [] palavrasChave = retirarSW(palavrasChaveSW);

    for(int i = 0; i < palavrasChave.length; i++)
    {
      indiceInvertido.create(palavrasChave[i], id); // insere todas as palavras no indice
    }

    return id;
  }

  public Livro readISBN(String isbn) throws Exception {
    ParIsbnId pii = indiceIndiretoISBN.read(ParIsbnId.hashIsbn(isbn));
    if (pii == null)
      return null;
    int id = pii.getId();
    return super.read(id);
  }

  public Livro [] readTitulo(String titulo) throws Exception{ // metodo para buscar livros por titulos
    String [] palavrasChaveSW = titulo.toLowerCase().split(" "); // divide o titulo em palavras
    String [] palavrasChave = retirarSW(palavrasChaveSW);
    if(palavrasChave.length == 0)
    {
      return null;
    }
    int [] dados = indiceInvertido.read(palavrasChave[0]); // faz um array com os indices referentes a primeira palavra buscada

    if(palavrasChave.length > 1) // se tiver mais de uma palavra
    {
      for(int i = 1; i < palavrasChave.length; i++) // percorre todas as palavras
      {
        int [] dados2 = indiceInvertido.read(palavrasChave[i]); // cria um segundo array com os indices referentes a Nzima palavra
        int [] interseção = intersecaoArrayInt(dados, dados2); // faz a interseção entre os arrays
        dados = interseção; // e passa a interseção para o array principal
      }
    }

    Livro [] resposta = new Livro[dados.length]; // cria um array de livros com o tamanho do resultado
    for(int i = 0; i < resposta.length; i++)
    {
      resposta[i] = super.read(dados[i]); // preenche com os livros encontrados
    }
    return resposta; //retorna o array construido
  }


  @Override
  public boolean delete(int id) throws Exception {
    Livro obj = super.read(id);
    if (obj != null) //se objeto existe
    {
      if (indiceIndiretoISBN.delete(ParIsbnId.hashIsbn(obj.getIsbn()))
          &&
          relLivrosDaCategoria.delete(new ParIntInt(obj.getIdCategoria(), obj.getID()))) //se conseguir apagar do indice e da categoria
        {
          String [] palavrasChaveSW = obj.getTitulo().toLowerCase().split(" "); // divide o titulo em palavras
          String [] palavrasChave = retirarSW(palavrasChaveSW); 
          for(int i = 0; i < palavrasChave.length; i++)
          {
            indiceInvertido.delete(palavrasChave[i], id); // deleta todos os indices referentes a palavra do indice invertido
          }
          return super.delete(id); //deleta o objeto
        }
    }
    

    return false;
  }

  @Override
  public boolean update(Livro novoLivro) throws Exception {
    Livro livroAntigo = super.read(novoLivro.getID());
    if (livroAntigo != null) {

      // Testa alteração do ISBN
      if (livroAntigo.getIsbn().compareTo(novoLivro.getIsbn()) != 0) {
        indiceIndiretoISBN.delete(ParIsbnId.hashIsbn(livroAntigo.getIsbn()));
        indiceIndiretoISBN.create(new ParIsbnId(novoLivro.getIsbn(), novoLivro.getID()));
      }

      // Testa alteração da categoria
      if (livroAntigo.getIdCategoria() != novoLivro.getIdCategoria()) {
        relLivrosDaCategoria.delete(new ParIntInt(livroAntigo.getIdCategoria(), livroAntigo.getID()));
        relLivrosDaCategoria.create(new ParIntInt(novoLivro.getIdCategoria(), novoLivro.getID()));
      }

      // Testa a alteração do título
      if(livroAntigo.getTitulo() != novoLivro.getTitulo()) {
        String [] palavrasChaveSW = livroAntigo.getTitulo().toLowerCase().split(" "); //divide o titulo antigo em palavras
        String [] palavrasChave = retirarSW(palavrasChaveSW);
        for(int i = 0; i < palavrasChave.length; i++)
        {
          indiceInvertido.delete(palavrasChave[i], livroAntigo.getID()); //apaga os indices destas palavras no indice invertido
        }

        String [] palavrasChaveSW2 = novoLivro.getTitulo().toLowerCase().split(" "); // divide o novo titulo em palavras
        String [] palavrasChave2 = retirarSW(palavrasChaveSW2);
        for(int i = 0; i < palavrasChave2.length; i++)
        {
          indiceInvertido.create(palavrasChave2[i], novoLivro.getID()); // cria os indices destas palavras no indice invertido 
        }
      }

      // Atualiza o livro
      return super.update(novoLivro);
    }
    return false;
  }

  //metodo para fazer a interseção de dois arrays de inteiros
  public int [] intersecaoArrayInt(int [] dados, int [] dados2) { 
    ArrayList<Integer> lista = new ArrayList<>(); //cria um arraylist
    for (int i = 0; i < dados.length; i++)//percorre a lista principal
    {
      for(int j = 0; j < dados2.length; j++) //percorre a lista secundária
      {
        if(dados[i] == dados2[j]) //se os dados da secundária aparecem na principal
        {
          lista.add(dados[i]); // adiciona ao arraylist
          break; //passa pro proximo
        }
      }
    }

    lista.sort(null); // organiza a lista
    int[] resposta = new int[lista.size()]; //cria um array de inteiros resposta
    for (int j = 0; j < lista.size(); j++)
    {
      resposta[j] = (int) lista.get(j); //coloca em resposta os valores salvos no arraylist
    }
    return resposta; //retorna a resposta

  }


  public String [] retirarSW(String [] palavrasChaveSW) throws Exception{

    ArrayList<String> stopWords = new ArrayList<>(); //inicio um arraylist para stopwords
    ArrayList<String> palavrasChave = new ArrayList<>(); //inicio um arraylist para as palavras chave (titulo)

    //criando um arraylist das stopWords
    BufferedReader buffRead = new BufferedReader(new FileReader("dados/stopwords.txt")); //abre o arquivo para leitura
    String sw = "";
    while ((sw = buffRead.readLine()) != null) //enquanto não ler null
    {
      stopWords.add(sw.toLowerCase().trim()); //adiciona ao arraylist (tudo minusculo para padronizar e tirando espaços)
    }
    buffRead.close(); //ao terminar fecha o bufferedreader

    /*
    funcionou mas queria fazer com arraylists usando a função remove all
    String [] teste = new String[stopWords.size()];
    for(int i = 0; i < teste.length; i++)
    {
      teste[i] = (String) stopWords.get(i);
    } 
    */

    //criando um arraylist com o titulo (ainda com stopwords)
    for(int i = 0; i < palavrasChaveSW.length; i++)
    {
      palavrasChave.add(palavrasChaveSW[i]); //adiciona todas as palavras chave ao arraylist
    }
    
    palavrasChave.removeAll(stopWords); //removo as stopwords das palavras chave
    
    /* 
    for(int i = 0; i < palavrasChaveSW.length; i++)
    {
      boolean conf = true;
      for(int j = 0; j < teste.length; j++)
      {
        if(palavrasChaveSW[i].equals(teste[j]))
        {
          conf = false;
          break;
        }
      }
      if(conf == true)
      {
        palavrasChave.add(palavrasChaveSW[i]);
      }
    }
    */
    
    palavrasChave.sort(null); // organiza a lista
    String[] resposta = new String[palavrasChave.size()]; //cria um array de Strings resposta
    for (int j = 0; j < palavrasChave.size(); j++)
    {
      resposta[j] = (String) palavrasChave.get(j); //coloca em resposta os valores salvos no arraylist
    }
    return resposta; //retorna a resposta
  }

}
