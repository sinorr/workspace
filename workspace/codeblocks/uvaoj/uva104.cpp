#include<iostream>
#include<string.h>
#define N 25
using namespace std;

double dp[N][N][N];
int p[N][N][N];
int n;

void print(int i, int j, int s )
{
    if( s == -1)
    {
        cout << i + 1 ;
        return;
    }
    print( i, p[i][j][s],s - 1);
    cout << ' ' << j + 1 ;
    return;
}

void bfs()
{
    int s, m;
    for( s = 1; s < n; s++ )
    {
        for( int k = 0; k < n; k++ )
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++ )
                {
                    double temp = dp[i][k][s-1]*dp[k][j][0];
                    if (dp[i][j][s] < temp)
                    {
                        dp[i][j][s] = temp;
                        p[i][j][s] = k;
                    }
                }
        int i;
        for( i = 0; i < n; i++ )
            if( dp[i][i][s] > 1.01 )
        {
            m = i;
            break;
        }
       if( i < n)
        break;
    }
     if( s >= n)
        cout << "no arbitrage sequence exists" ;
    else
        print( m, m, s);
        cout << endl;
}

int main()
{
    while( cin >> n)
    {
        memset(dp,0,sizeof(dp));
        memset(p,0,sizeof(p));
        for(int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
        {
            if(i==j)
                dp[i][j][0]=1;
            else cin >> dp[i][j][0];
            p[i][j][0] = j;
        }

        bfs();
    }
    return 0;
}
