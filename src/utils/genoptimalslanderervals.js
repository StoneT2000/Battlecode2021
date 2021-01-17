// t\left(x\right)=\operatorname{floor}\left(\left(\frac{1}{50}+0.03e^{-0.001x}\right)x\right)\cdot50-x

function genOptimal() {
  const returns = (x) =>{
    return Math.floor(
      ((1/50 + 0.03 * Math.exp(-0.001 * x)) * x)
      ) * 50 - x;
  }

  let lastBest = -1;
  let vals = [];
  for (let x = 0; x <= 1000; x++) {
    let val = returns(x);
    if (val > lastBest) {
      vals.push(x);
      lastBest = val;
    }
  }
  return vals;
}

let optimalSlandBuildVals = genOptimal();
console.log(optimalSlandBuildVals);
function findOptimalUnderX(x) {
  
  let low = 0;
  let high = optimalSlandBuildVals.length;
  while (low < high) {
      let mid = Math.floor((low + high) / 2);
      if (optimalSlandBuildVals[mid] > x) {
          high = mid;
      } else if (optimalSlandBuildVals[mid] < x) {
        low = mid + 1;
      } else {
        return x;
      }
  }
  return {low, val: optimalSlandBuildVals[low - 1]}
}
console.log(findOptimalUnderX(464));